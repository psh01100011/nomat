package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.dto.UploadImageResponse;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class AssetService {

    private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final S3Client s3Client;
    private final AssetS3Properties properties;

    @Transactional
    public UploadImageResponse uploadImage(Long userId, MultipartFile file) {
        User uploader = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));

        validateImage(file);
        validateS3Configuration();

        String originalFilename = getOriginalFilename(file);
        String extension = getExtension(originalFilename);
        String storageKey = createImageStorageKey(extension);
        String url = createPublicUrl(storageKey);

        uploadToS3(file, storageKey);

        Asset asset = Asset.createImage(
                uploader,
                originalFilename,
                storageKey,
                url,
                file.getContentType(),
                file.getSize()
        );

        return UploadImageResponse.from(assetRepository.save(asset));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidRequest();
        }

        if (file.getSize() > properties.getMaxImageSizeBytes()) {
            throw invalidRequest();
        }

        String extension = getExtension(getOriginalFilename(file));
        String expectedContentType = IMAGE_CONTENT_TYPES.get(extension);
        if (expectedContentType == null || !expectedContentType.equals(file.getContentType())) {
            throw invalidRequest();
        }
    }

    private void validateS3Configuration() {
        if (!StringUtils.hasText(properties.getBucket())) {
            throw internalServerError();
        }
    }

    private String getOriginalFilename(MultipartFile file) {
        String originalFilename = StringUtils.getFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(originalFilename)) {
            throw invalidRequest();
        }

        return originalFilename;
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw invalidRequest();
        }

        int extensionStartIndex = filename.lastIndexOf('.');
        if (extensionStartIndex < 0 || extensionStartIndex == filename.length() - 1) {
            throw invalidRequest();
        }

        return filename.substring(extensionStartIndex + 1).toLowerCase();
    }

    private String createImageStorageKey(String extension) {
        LocalDate today = LocalDate.now();
        String prefix = properties.normalizedImagePrefix();
        String filename = UUID.randomUUID() + "." + extension;

        if (!StringUtils.hasText(prefix)) {
            return "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), filename);
        }

        return "%s/%d/%02d/%s".formatted(prefix, today.getYear(), today.getMonthValue(), filename);
    }

    private String createPublicUrl(String storageKey) {
        String baseUrl = properties.resolvedPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("S3 public base URL or bucket must be configured.");
        }

        return baseUrl + "/" + storageKey;
    }

    private void uploadToS3(MultipartFile file, String storageKey) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException exception) {
            throw internalServerError();
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private BusinessException internalServerError() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error");
    }
}
