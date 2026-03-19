package com.portfolio.aicontentstudio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enable JPA Auditing for @CreatedDate and @LastModifiedDate.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
