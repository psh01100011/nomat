package com.dogdog.nomat.domain.room.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.room.events")
public class RoomEventProperties {

    @NotBlank
    private String channel = "nomat:rooms:events";
}
