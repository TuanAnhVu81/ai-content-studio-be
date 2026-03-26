package com.portfolio.aicontentstudio.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth.session")
@Getter
@Setter
public class AuthSessionProperties {

    private int maxActiveSessionsPerUser = 5;
    private long lockTimeoutSeconds = 5;

    @PostConstruct
    void validate() {
        if (maxActiveSessionsPerUser < 1) {
            throw new IllegalStateException("max-active-sessions-per-user must be at least 1");
        }
        if (lockTimeoutSeconds < 1) {
            throw new IllegalStateException("lock-timeout-seconds must be at least 1");
        }
    }
}
