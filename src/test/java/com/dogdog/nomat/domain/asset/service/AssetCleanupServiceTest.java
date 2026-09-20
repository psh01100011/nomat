package com.dogdog.nomat.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.config.AssetCleanupProperties;
import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.map.entity.MapStatus;
import com.dogdog.nomat.domain.map.entity.QuestionStatus;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class AssetCleanupServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private S3Client s3Client;

    @Mock
    private OrphanedAssetCleanupService orphanedAssetCleanupService;

    private AssetCleanupService assetCleanupService;

    @BeforeEach
    void setUp() {
        AssetS3Properties s3Properties = new AssetS3Properties();
        s3Properties.setBucket("nomat-assets");

        AssetCleanupProperties cleanupProperties = new AssetCleanupProperties();
        cleanupProperties.setTempRetentionHours(24);
        cleanupProperties.setOrphanRetentionHours(168);
        cleanupProperties.setBatchSize(100);
        cleanupProperties.setScanBatchSize(500);

        assetCleanupService = new AssetCleanupService(
                assetRepository,
                s3Client,
                s3Properties,
                cleanupProperties,
                orphanedAssetCleanupService
        );
    }

    @Test
    void cleanupExpiredTempAssetsDeletesS3ObjectsAndMarksAssetsDeleted() {
        Asset asset = imageAsset(1L, "uploads/images/old.png");
        given(assetRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(AssetStatus.TEMP),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(asset));
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willReturn(DeleteObjectResponse.builder().build());

        int cleanedCount = assetCleanupService.cleanupExpiredTempAssets();

        assertThat(cleanedCount).isEqualTo(1);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        assertThat(asset.getDeletedAt()).isNotNull();

        ArgumentCaptor<DeleteObjectRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteRequestCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteRequestCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("nomat-assets");
        assertThat(deleteRequest.key()).isEqualTo("uploads/images/old.png");
    }

    @Test
    void cleanupExpiredTempAssetsKeepsTempStatusWhenS3DeleteFails() {
        Asset asset = imageAsset(1L, "uploads/images/old.png");
        given(assetRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(AssetStatus.TEMP),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(asset));
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().message("delete failed").build());

        int cleanedCount = assetCleanupService.cleanupExpiredTempAssets();

        assertThat(cleanedCount).isZero();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TEMP);
        assertThat(asset.getDeletedAt()).isNull();
    }

    @Test
    void cleanupExpiredTempAssetsUsesConfiguredRetentionAndBatchSize() {
        given(assetRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(AssetStatus.TEMP),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of());

        int cleanedCount = assetCleanupService.cleanupExpiredTempAssets();

        assertThat(cleanedCount).isZero();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(assetRepository).findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(AssetStatus.TEMP),
                cutoffCaptor.capture(),
                pageableCaptor.capture()
        );

        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusHours(23));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void scanUnreferencedAssetsMarksOnlyUnusedAssetsAndAdvancesCursor() {
        Asset asset = imageAsset(1L, "uploads/images/unused.png");
        asset.attach();
        given(assetRepository.findAssetIdsAfter(
                eq(AssetStatus.ATTACHED),
                eq(0L),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L));
        given(assetRepository.findUnreferencedAssetsByIdIn(
                eq(List.of(1L, 2L)),
                eq(AssetStatus.ATTACHED),
                eq(UserStatus.DELETED),
                eq(MapStatus.DELETED),
                eq(QuestionStatus.DELETED)
        )).willReturn(List.of(asset));

        AssetCleanupService.OrphanScanSummary summary = assetCleanupService.scanUnreferencedAssets(0L);

        assertThat(summary.scannedCount()).isEqualTo(2);
        assertThat(summary.orphanedCount()).isEqualTo(1);
        assertThat(summary.nextCursor()).isEqualTo(2L);
        assertThat(summary.wrapped()).isFalse();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ORPHANED);
        assertThat(asset.getOrphanedAt()).isNotNull();
    }

    @Test
    void scanUnreferencedAssetsWrapsToBeginningAfterLastAsset() {
        given(assetRepository.findAssetIdsAfter(
                eq(AssetStatus.ATTACHED),
                eq(500L),
                any(Pageable.class)
        )).willReturn(List.of());
        given(assetRepository.findAssetIdsAfter(
                eq(AssetStatus.ATTACHED),
                eq(0L),
                any(Pageable.class)
        )).willReturn(List.of(1L));
        given(assetRepository.findUnreferencedAssetsByIdIn(
                eq(List.of(1L)),
                eq(AssetStatus.ATTACHED),
                eq(UserStatus.DELETED),
                eq(MapStatus.DELETED),
                eq(QuestionStatus.DELETED)
        )).willReturn(List.of());

        AssetCleanupService.OrphanScanSummary summary = assetCleanupService.scanUnreferencedAssets(500L);

        assertThat(summary.scannedCount()).isEqualTo(1);
        assertThat(summary.nextCursor()).isEqualTo(1L);
        assertThat(summary.wrapped()).isTrue();
    }

    @Test
    void cleanupExpiredOrphanedAssetsSummarizesPerAssetOutcomes() {
        given(assetRepository.findOrphanedAssetIdsBefore(
                eq(AssetStatus.ORPHANED),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L, 3L));
        given(orphanedAssetCleanupService.cleanup(1L))
                .willReturn(OrphanedAssetCleanupService.CleanupOutcome.DELETED);
        given(orphanedAssetCleanupService.cleanup(2L))
                .willReturn(OrphanedAssetCleanupService.CleanupOutcome.RESTORED);
        given(orphanedAssetCleanupService.cleanup(3L))
                .willReturn(OrphanedAssetCleanupService.CleanupOutcome.FAILED);

        AssetCleanupService.OrphanCleanupSummary summary = assetCleanupService.cleanupExpiredOrphanedAssets();

        assertThat(summary.deletedCount()).isEqualTo(1);
        assertThat(summary.restoredCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(1);
    }

    private Asset imageAsset(Long id, String storageKey) {
        User uploader = User.create("testuser", "encoded-password", "tester");
        Asset asset = Asset.createImage(
                uploader,
                "profile.png",
                storageKey,
                "https://cdn.nomat.com/" + storageKey,
                "image/png",
                1024L
        );
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
