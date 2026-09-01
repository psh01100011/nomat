package com.dogdog.nomat.domain.asset.model;

import java.util.Locale;

public enum ImageUploadPurpose {

    PROFILE(512, 512),
    MAP_THUMBNAIL(1280, 720),
    QUESTION_IMAGE(1600, 900);

    private final int width;
    private final int height;

    ImageUploadPurpose(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public static ImageUploadPurpose parse(String value) {
        if (value == null || value.isBlank()) {
            return QUESTION_IMAGE;
        }

        try {
            return ImageUploadPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
