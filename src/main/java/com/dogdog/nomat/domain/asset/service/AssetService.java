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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@Slf4j
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

        try {
            return UploadImageResponse.from(assetRepository.saveAndFlush(asset));
        } catch (RuntimeException exception) {
            deleteFromS3Quietly(storageKey);
            throw exception;
        }
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

        if (!matchesImageMagicBytes(file, extension)) {
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

    private boolean matchesImageMagicBytes(MultipartFile file, String extension) {
        byte[] header;
        try {
            header = file.getInputStream().readNBytes(12);
        } catch (IOException exception) {
            throw invalidRequest();
        }

        return switch (extension) {
            case "jpg", "jpeg" -> isJpeg(header);
            case "png" -> isPng(header);
            case "webp" -> isWebp(header);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return header.length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private void deleteFromS3Quietly(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            log.warn("Failed to delete S3 object after asset save failure. bucket={}, key={}",
                    properties.getBucket(),
                    storageKey,
                    exception
            );
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private BusinessException internalServerError() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error");
    }
}
