package com.dogdog.nomat.domain.asset.service;

import com.dogdog.nomat.domain.asset.config.AssetS3Properties;
import com.dogdog.nomat.domain.asset.dto.UploadImageResponse;
import com.dogdog.nomat.domain.asset.entity.Asset;
import com.dogdog.nomat.domain.asset.model.ImageUploadPurpose;
import com.dogdog.nomat.domain.asset.repository.AssetRepository;
import com.dogdog.nomat.domain.user.entity.User;
import com.dogdog.nomat.domain.user.entity.UserStatus;
import com.dogdog.nomat.domain.user.repository.UserRepository;
import com.dogdog.nomat.global.exception.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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
    private static final String WEBP_EXTENSION = "webp";
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final int MAX_IMAGE_LONG_SIDE = 4096;
    private static final float WEBP_QUALITY = 0.82f;

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final S3Client s3Client;
    private final AssetS3Properties properties;

    @Transactional
    public UploadImageResponse uploadImage(Long userId, MultipartFile file) {
        return uploadImage(userId, file, null);
    }

    @Transactional
    public UploadImageResponse uploadImage(Long userId, MultipartFile file, String purposeValue) {
        User uploader = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "invalid_token"));

        ImageUploadPurpose purpose = parsePurpose(purposeValue);
        validateImage(file);
        validateS3Configuration();

        String originalFilename = getOriginalFilename(file);
        String extension = getExtension(originalFilename);
        ProcessedImage processedImage = processImage(file, purpose);
        String storageKey = createImageStorageKey(WEBP_EXTENSION);
        String url = createPublicUrl(storageKey);

        uploadToS3(processedImage, storageKey);

        Asset asset = Asset.createImage(
                uploader,
                originalFilename,
                storageKey,
                url,
                WEBP_CONTENT_TYPE,
                (long) processedImage.bytes().length
        );

        try {
            Asset savedAsset = assetRepository.saveAndFlush(asset);
            log.info(
                    "event=image_asset_uploaded assetId={} userId={} purpose={} sizeBytes={} width={} height={}",
                    savedAsset.getId(),
                    userId,
                    purpose,
                    processedImage.bytes().length,
                    processedImage.width(),
                    processedImage.height()
            );
            return UploadImageResponse.from(savedAsset);
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "image_too_large");
        }

        String extension = getExtension(getOriginalFilename(file));
        String expectedContentType = IMAGE_CONTENT_TYPES.get(extension);
        if (expectedContentType == null || !expectedContentType.equals(file.getContentType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "unsupported_image_type");
        }

        if (!matchesImageMagicBytes(file, extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "unsupported_image_type");
        }
    }

    private ImageUploadPurpose parsePurpose(String purposeValue) {
        ImageUploadPurpose purpose = ImageUploadPurpose.parse(purposeValue);
        if (purpose == null) {
            throw invalidRequest();
        }

        return purpose;
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

    private ProcessedImage processImage(MultipartFile file, ImageUploadPurpose purpose) {
        try {
            BufferedImage sourceImage = ImageIO.read(file.getInputStream());
            if (sourceImage == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "unsupported_image_type");
            }

            validateResolution(sourceImage);
            BufferedImage resizedImage = resizeAndCrop(sourceImage, purpose.width(), purpose.height());
            return new ProcessedImage(
                    encodeWebp(resizedImage),
                    resizedImage.getWidth(),
                    resizedImage.getHeight()
            );
        } catch (IOException | UncheckedIOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "image_processing_failed");
        }
    }

    private void validateResolution(BufferedImage sourceImage) {
        int longSide = Math.max(sourceImage.getWidth(), sourceImage.getHeight());
        if (longSide > MAX_IMAGE_LONG_SIDE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "image_too_large");
        }
    }

    private BufferedImage resizeAndCrop(BufferedImage sourceImage, int targetWidth, int targetHeight) {
        double sourceRatio = (double) sourceImage.getWidth() / sourceImage.getHeight();
        double targetRatio = (double) targetWidth / targetHeight;
        int cropWidth = sourceImage.getWidth();
        int cropHeight = sourceImage.getHeight();

        if (sourceRatio > targetRatio) {
            cropWidth = (int) Math.round(sourceImage.getHeight() * targetRatio);
        } else if (sourceRatio < targetRatio) {
            cropHeight = (int) Math.round(sourceImage.getWidth() / targetRatio);
        }

        int cropX = (sourceImage.getWidth() - cropWidth) / 2;
        int cropY = (sourceImage.getHeight() - cropHeight) / 2;
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = outputImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(
                    sourceImage,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    cropX,
                    cropY,
                    cropX + cropWidth,
                    cropY + cropHeight,
                    null
            );
            return outputImage;
        } finally {
            graphics.dispose();
        }
    }

    private byte[] encodeWebp(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(WEBP_EXTENSION);
        if (!writers.hasNext()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "image_processing_failed");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                setCompressionType(writeParam);
                writeParam.setCompressionQuality(WEBP_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "image_processing_failed");
        } finally {
            writer.dispose();
        }
    }

    private void setCompressionType(ImageWriteParam writeParam) {
        String[] compressionTypes = writeParam.getCompressionTypes();
        if (compressionTypes == null || compressionTypes.length == 0) {
            return;
        }

        for (String compressionType : compressionTypes) {
            if ("Lossy".equalsIgnoreCase(compressionType)) {
                writeParam.setCompressionType(compressionType);
                return;
            }
        }

        writeParam.setCompressionType(compressionTypes[0]);
    }

    private void uploadToS3(ProcessedImage processedImage, String storageKey) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .contentType(WEBP_CONTENT_TYPE)
                .contentLength((long) processedImage.bytes().length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(processedImage.bytes()));
        } catch (SdkException exception) {
            log.error(
                    "event=image_asset_upload_failed storageKey={} errorType={}",
                    storageKey,
                    exception.getClass().getSimpleName(),
                    exception
            );
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
            log.warn("event=orphaned_image_asset_cleanup_failed storageKey={}",
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

    private record ProcessedImage(
            byte[] bytes,
            int width,
            int height
    ) {
    }
}
