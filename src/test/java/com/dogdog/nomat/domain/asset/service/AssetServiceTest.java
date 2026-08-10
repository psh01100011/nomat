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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

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
                "image".getBytes()
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(uploader));
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        given(assetRepository.save(any(Asset.class))).willAnswer(invocation -> {
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
        verify(assetRepository).save(assetCaptor.capture());

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
    void uploadImageRejectsUnknownUserId() {
        MockMultipartFile file = imageFile("profile.png", "image/png");
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.uploadImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(assetRepository, never()).save(any(Asset.class));
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
        verify(assetRepository, never()).save(any(Asset.class));
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
        verify(assetRepository, never()).save(any(Asset.class));
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
        verify(assetRepository, never()).save(any(Asset.class));
    }

    private MockMultipartFile imageFile(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, "image".getBytes());
    }
}
