package com.dogdog.nomat.domain.asset.dto;

import com.dogdog.nomat.domain.asset.entity.Asset;

public record UploadImageResponse(
        Long assetId,
        String url
) {

    public static UploadImageResponse from(Asset asset) {
        return new UploadImageResponse(asset.getId(), asset.getUrl());
    }
}
