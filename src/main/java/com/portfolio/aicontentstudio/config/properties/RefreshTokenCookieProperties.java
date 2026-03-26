package com.portfolio.aicontentstudio.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
@Getter
@Setter
public class RefreshTokenCookieProperties {

    private String name = "refresh_token";
    private boolean secure = true;
    private String sameSite = "None";
    private String path = "/api/v1/auth";
    private String domain;
    private long maxAgeSeconds = 604800;

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("Refresh cookie name must not be blank");
        }
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("Refresh cookie path must not be blank");
        }
        if (!StringUtils.hasText(sameSite)) {
            throw new IllegalStateException("Refresh cookie same-site policy must not be blank");
        }
        if ("none".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None requires secure=true for refresh token cookies");
        }
    }
}
