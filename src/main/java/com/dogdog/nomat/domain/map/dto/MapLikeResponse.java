package com.dogdog.nomat.domain.map.dto;

public record MapLikeResponse(
        Long mapId,
        boolean liked,
        long likeCount
) {
}
