package com.dogdog.nomat.domain.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MapFavoriteId implements Serializable {

    @Column(name = "map_id", nullable = false)
    private Long mapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
