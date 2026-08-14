package com.dogdog.nomat.domain.map.dto;

public record MapFavoriteResponse(
        Long mapId,
        boolean favorited,
        long favoriteCount
) {
}
