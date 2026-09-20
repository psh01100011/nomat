package com.dogdog.nomat.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class OrphanedAssetCleanupServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private S3Client s3Client;

    private OrphanedAssetCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        AssetS3Properties properties = new AssetS3Properties();
        properties.setBucket("nomat-assets");
        cleanupService = new OrphanedAssetCleanupService(assetRepository, s3Client, properties);
    }

    @Test
    void cleanupDeletesUnreferencedOrphanFromS3() {
        Asset asset = orphanedAsset();
        given(assetRepository.findByIdForUpdate(1L)).willReturn(Optional.of(asset));
        given(assetRepository.existsActiveReference(
                1L, UserStatus.DELETED, MapStatus.DELETED, QuestionStatus.DELETED
        )).willReturn(false);
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willReturn(DeleteObjectResponse.builder().build());

        OrphanedAssetCleanupService.CleanupOutcome outcome = cleanupService.cleanup(1L);

        assertThat(outcome).isEqualTo(OrphanedAssetCleanupService.CleanupOutcome.DELETED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
    }

    @Test
    void cleanupRestoresAssetWhenReferenceWasAddedDuringGracePeriod() {
        Asset asset = orphanedAsset();
        given(assetRepository.findByIdForUpdate(1L)).willReturn(Optional.of(asset));
        given(assetRepository.existsActiveReference(
                1L, UserStatus.DELETED, MapStatus.DELETED, QuestionStatus.DELETED
        )).willReturn(true);

        OrphanedAssetCleanupService.CleanupOutcome outcome = cleanupService.cleanup(1L);

        assertThat(outcome).isEqualTo(OrphanedAssetCleanupService.CleanupOutcome.RESTORED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ATTACHED);
        assertThat(asset.getOrphanedAt()).isNull();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupKeepsOrphanedStatusWhenS3DeleteFails() {
        Asset asset = orphanedAsset();
        given(assetRepository.findByIdForUpdate(1L)).willReturn(Optional.of(asset));
        given(assetRepository.existsActiveReference(
                1L, UserStatus.DELETED, MapStatus.DELETED, QuestionStatus.DELETED
        )).willReturn(false);
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().message("delete failed").build());

        OrphanedAssetCleanupService.CleanupOutcome outcome = cleanupService.cleanup(1L);

        assertThat(outcome).isEqualTo(OrphanedAssetCleanupService.CleanupOutcome.FAILED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ORPHANED);
    }

    private Asset orphanedAsset() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        Asset asset = Asset.createImage(
                uploader,
                "profile.png",
                "uploads/images/unused.png",
                "https://cdn.nomat.com/uploads/images/unused.png",
                "image/png",
                1024L
        );
        ReflectionTestUtils.setField(asset, "id", 1L);
        asset.attach();
        asset.markOrphaned(LocalDateTime.now().minusDays(8));
        return asset;
    }
}
