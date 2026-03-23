package com.portfolio.aicontentstudio.modules.ai_log.service;

import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AiUsageLogService using JUnit 5, Mockito, and AssertJ.
 * Focuses on async-safe persistence behavior and default token normalization.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageLogServiceTest {

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @InjectMocks
    private AiUsageLogService aiUsageLogService;

    @Captor
    private ArgumentCaptor<AiUsageLog> usageLogCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: logUsage(User user, Content content, Integer promptTokens, Integer responseTokens, Integer totalTokens, String modelName)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void logUsage_AllValuesPresent_SavesUsageLog() {
        // Given
        User user = createUser();
        Content content = createContent();

        // When
        aiUsageLogService.logUsage(user, content, 120, 250, 370, "gemini-3-flash");

        // Then
        verify(aiUsageLogRepository, times(1)).save(usageLogCaptor.capture());
        AiUsageLog capturedLog = usageLogCaptor.getValue();
        assertThat(capturedLog.getUser()).isEqualTo(user);
        assertThat(capturedLog.getContent()).isEqualTo(content);
        assertThat(capturedLog.getPromptTokens()).isEqualTo(120);
        assertThat(capturedLog.getResponseTokens()).isEqualTo(250);
        assertThat(capturedLog.getTotalTokens()).isEqualTo(370);
        assertThat(capturedLog.getModelName()).isEqualTo("gemini-3-flash");
    }

    @Test
    void logUsage_NullTokenValues_DefaultsMissingValuesToZero() {
        // Given
        User user = createUser();
        Content content = createContent();

        // When
        aiUsageLogService.logUsage(user, content, null, null, null, "gemini-3-flash");

        // Then
        verify(aiUsageLogRepository, times(1)).save(usageLogCaptor.capture());
        AiUsageLog capturedLog = usageLogCaptor.getValue();
        assertThat(capturedLog.getPromptTokens()).isZero();
        assertThat(capturedLog.getResponseTokens()).isZero();
        assertThat(capturedLog.getTotalTokens()).isZero();
    }

    @Test
    void logUsage_RepositoryThrowsException_DoesNotPropagateException() {
        // Given
        User user = createUser();
        Content content = createContent();
        given(aiUsageLogRepository.save(any(AiUsageLog.class))).willThrow(new RuntimeException("DB error"));

        // When
        // Then
        assertThatCode(() -> aiUsageLogService.logUsage(user, content, 120, 250, 370, "gemini-3-flash"))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private User createUser() {
        User user = User.builder()
                .email("tester@example.com")
                .passwordHash("encoded-password")
                .fullName("Test User")
                .status(AccountStatus.ACTIVE)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Content createContent() {
        Content content = Content.builder().build();
        content.setId(UUID.randomUUID());
        return content;
    }
}
