package com.dogdog.nomat.global.http;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.http")
public class ClientIpProperties {

    private List<String> trustedProxyCidrs = new ArrayList<>();
}
