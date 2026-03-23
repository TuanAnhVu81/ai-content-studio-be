package com.portfolio.aicontentstudio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Async thread pool configuration for non-blocking background tasks and SSE streaming.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AiApp-");
        executor.initialize();
        return executor;
    }

    /**
     * Replaces the default SimpleAsyncTaskExecutor in Spring MVC with our robust ThreadPoolTaskExecutor.
     * Crucial for production-ready SSE Streaming (resolves the WARN log).
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(taskExecutor());
    }
}

