package com.dogdog.nomat.domain.asset.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dogdog.nomat.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssetTest {

    @Test
    void createImageCreatesTempReadyImageAsset() {
        User uploader = User.create("testuser", "encoded-password", "tester");

        Asset asset = Asset.createImage(
                uploader,
                "profile.png",
                "uploads/images/2026/08/profile.png",
                "https://cdn.nomat.com/uploads/images/2026/08/profile.png",
                "image/png",
                1024L
        );

        assertThat(asset.getUploader()).isEqualTo(uploader);
        assertThat(asset.getAssetType()).isEqualTo(AssetType.IMAGE);
        assertThat(asset.getOriginalFilename()).isEqualTo("profile.png");
        assertThat(asset.getStorageKey()).isEqualTo("uploads/images/2026/08/profile.png");
        assertThat(asset.getUrl()).isEqualTo("https://cdn.nomat.com/uploads/images/2026/08/profile.png");
        assertThat(asset.getMimeType()).isEqualTo("image/png");
        assertThat(asset.getSizeBytes()).isEqualTo(1024L);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TEMP);
        assertThat(asset.getProcessingStatus()).isEqualTo(AssetProcessingStatus.READY);
        assertThat(asset.getDeletedAt()).isNull();
    }

    @Test
    void attachChangesTempAssetToAttached() {
        Asset asset = imageAsset();

        asset.attach();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ATTACHED);
        assertThat(asset.getDeletedAt()).isNull();
    }

    @Test
    void attachKeepsAttachedAssetAttached() {
        Asset asset = imageAsset();
        asset.attach();

        asset.attach();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ATTACHED);
        assertThat(asset.getDeletedAt()).isNull();
    }

    @Test
    void attachRestoresOrphanedAsset() {
        Asset asset = imageAsset();
        asset.attach();
        asset.markOrphaned(LocalDateTime.now());

        asset.attach();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ATTACHED);
        assertThat(asset.getOrphanedAt()).isNull();
    }

    @Test
    void markOrphanedStartsGracePeriodOnlyForAttachedAsset() {
        Asset asset = imageAsset();
        LocalDateTime orphanedAt = LocalDateTime.now();

        asset.markOrphaned(orphanedAt);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TEMP);

        asset.attach();
        asset.markOrphaned(orphanedAt);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ORPHANED);
        assertThat(asset.getOrphanedAt()).isEqualTo(orphanedAt);
    }

    @Test
    void attachRejectsDeletedAsset() {
        Asset asset = imageAsset();
        asset.delete();

        assertThatThrownBy(asset::attach)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Deleted asset cannot be attached.");
    }

    @Test
    void attachRejectsAssetThatIsNotReady() {
        Asset asset = imageAsset();
        ReflectionTestUtils.setField(asset, "processingStatus", AssetProcessingStatus.PROCESSING);

        assertThatThrownBy(asset::attach)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Asset is not ready to be attached.");
    }

    @Test
    void deleteChangesTempAssetToDeleted() {
        Asset asset = imageAsset();

        asset.delete();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        assertThat(asset.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteKeepsDeletedAssetDeleted() {
        Asset asset = imageAsset();
        asset.delete();
        LocalDateTime deletedAt = asset.getDeletedAt();

        asset.delete();

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        assertThat(asset.getDeletedAt()).isEqualTo(deletedAt);
    }

    private Asset imageAsset() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        return Asset.createImage(
                uploader,
                "profile.png",
                "uploads/images/2026/08/profile.png",
                "https://cdn.nomat.com/uploads/images/2026/08/profile.png",
                "image/png",
                1024L
        );
    }
}
