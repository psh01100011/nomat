package com.dogdog.nomat.domain.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.dto.UploadImageResponse;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.entity.AssetProcessingStatus;
import com.dogdog.nomat.domain.asset.entity.AssetStatus;
import com.dogdog.nomat.domain.asset.entity.AssetType;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private S3Client s3Client;

    private AssetS3Properties properties;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        properties = new AssetS3Properties();
        properties.setBucket("nomat-assets");
        properties.setRegion("ap-northeast-2");
        properties.setPublicBaseUrl("https://cdn.nomat.com");
        properties.setImagePrefix("uploads/images");
        properties.setMaxImageSizeBytes(10 * 1024 * 1024);

        assetService = new AssetService(userRepository, assetRepository, s3Client, properties);
    }

    @Test
    void uploadImageUploadsFileToS3AndSavesTempAsset() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        ReflectionTestUtils.setField(uploader, "id", 1L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                pngBytes()
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.saveAndFlush(any(Asset.class))).willAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", 10L);
            return asset;
        });

        UploadImageResponse response = assetService.uploadImage(1L, file);

        assertThat(response.assetId()).isEqualTo(10L);
        assertThat(response.url()).startsWith("https://cdn.nomat.com/uploads/images/");
        assertThat(response.url()).endsWith(".png");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("nomat-assets");
        assertThat(request.key()).startsWith("uploads/images/");
        assertThat(request.key()).endsWith(".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(file.getSize());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).saveAndFlush(assetCaptor.capture());

        Asset savedAsset = assetCaptor.getValue();
        assertThat(savedAsset.getUploader()).isEqualTo(uploader);
        assertThat(savedAsset.getAssetType()).isEqualTo(AssetType.IMAGE);
        assertThat(savedAsset.getOriginalFilename()).isEqualTo("profile.png");
        assertThat(savedAsset.getStorageKey()).isEqualTo(request.key());
        assertThat(savedAsset.getUrl()).isEqualTo(response.url());
        assertThat(savedAsset.getMimeType()).isEqualTo("image/png");
        assertThat(savedAsset.getSizeBytes()).isEqualTo(file.getSize());
        assertThat(savedAsset.getStatus()).isEqualTo(AssetStatus.TEMP);
        assertThat(savedAsset.getProcessingStatus()).isEqualTo(AssetProcessingStatus.READY);
    }

    @Test
    void uploadImageAcceptsWebpMagicBytes() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.webp",
                "image/webp",
                webpBytes()
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.saveAndFlush(any(Asset.class))).willAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", 10L);
            return asset;
        });

        UploadImageResponse response = assetService.uploadImage(1L, file);

        assertThat(response.assetId()).isEqualTo(10L);
        assertThat(response.url()).endsWith(".webp");
    }

    @Test
    void uploadImageAcceptsJpegMagicBytes() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                jpegBytes()
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.saveAndFlush(any(Asset.class))).willAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "id", 10L);
            return asset;
        });

        UploadImageResponse response = assetService.uploadImage(1L, file);

        assertThat(response.assetId()).isEqualTo(10L);
        assertThat(response.url()).endsWith(".jpg");
    }


    @Test
    void uploadImageRejectsUnknownUserId() {
        MockMultipartFile file = imageFile("profile.png", "image/png");
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).saveAndFlush(any(Asset.class));
    }

    @Test
    void uploadImageRejectsUnsupportedImageExtension() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = imageFile("profile.gif", "image/gif");
        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).saveAndFlush(any(Asset.class));
    }

    @Test
    void uploadImageRejectsMismatchedContentType() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = imageFile("profile.png", "image/jpeg");
        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).saveAndFlush(any(Asset.class));
    }

    @Test
    void uploadImageRejectsMismatchedMagicBytes() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "profile.png",
                "image/png",
                "not-png".getBytes()
        );
        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).saveAndFlush(any(Asset.class));
    }

    @Test
    void uploadImageRejectsFileOverTenMegabytes() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        properties.setMaxImageSizeBytes(1);
        MockMultipartFile file = imageFile("profile.png", "image/png");
        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_request");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).saveAndFlush(any(Asset.class));
    }

    @Test
    void uploadImageDeletesS3ObjectWhenAssetSaveFails() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = imageFile("profile.png", "image/png");
        RuntimeException saveFailure = new RuntimeException("save failed");

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.saveAndFlush(any(Asset.class))).willThrow(saveFailure);

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isSameAs(saveFailure);

        ArgumentCaptor<DeleteObjectRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteRequestCaptor.capture());

        DeleteObjectRequest deleteRequest = deleteRequestCaptor.getValue();
        assertThat(deleteRequest.bucket()).isEqualTo("nomat-assets");
        assertThat(deleteRequest.key()).startsWith("uploads/images/");
        assertThat(deleteRequest.key()).endsWith(".png");
    }

    @Test
    void uploadImageKeepsOriginalExceptionWhenCompensationDeleteFails() {
        User uploader = User.create("testuser", "encoded-password", "tester");
        MockMultipartFile file = imageFile("profile.png", "image/png");
        RuntimeException saveFailure = new RuntimeException("save failed");

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.saveAndFlush(any(Asset.class))).willThrow(saveFailure);
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().message("delete failed").build());

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isSameAs(saveFailure);
    }

    private MockMultipartFile imageFile(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, pngBytes());
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
    }

    private byte[] jpegBytes() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                0x00, 0x00, 0x00
        };
    }

    private byte[] webpBytes() {
        return new byte[] {
                0x52, 0x49, 0x46, 0x46,
                0x00, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50
        };
    }
}
