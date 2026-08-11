package com.dogdog.nomat.domain.asset.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.asset.s3")
public class AssetS3Properties {

    @NotBlank
    private String bucket;

    @NotBlank
    private String region;

    @NotBlank
    private String publicBaseUrl;

    @NotBlank
    private String imagePrefix;

    @NotBlank
    private String audioPrefix;

    @Positive
    private long maxImageSizeBytes;

    public String normalizedImagePrefix() {
        return trimSlashes(imagePrefix);
    }

    public String normalizedAudioPrefix() {
        return trimSlashes(audioPrefix);
    }

    public String resolvedPublicBaseUrl() {
        return trimTrailingSlashes(publicBaseUrl);
    }

    private String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String trimTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }
}
