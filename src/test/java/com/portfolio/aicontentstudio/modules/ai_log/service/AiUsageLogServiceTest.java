package com.portfolio.aicontentstudio.modules.ai_log.service;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for AiUsageLogService using JUnit 5, Mockito, and AssertJ.
 * Focuses on after-commit orchestration and command normalization.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageLogServiceTest {

    @Mock
    private AiUsageLogAsyncWriter aiUsageLogAsyncWriter;

    @InjectMocks
    private AiUsageLogService aiUsageLogService;

    @Captor
    private ArgumentCaptor<AiUsageLogCommand> commandCaptor;

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: logUsage(User user, Content content, Integer promptTokens, Integer responseTokens, Integer totalTokens, String modelName)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void logUsage_NoActiveTransaction_DelegatesToAsyncWriterImmediately() {
        // Given
        User user = createUser();
        Content content = createContent();

        // When
        aiUsageLogService.logUsage(user, content, 120, 250, 370, "gemini-3-flash");

        // Then
        verify(aiUsageLogAsyncWriter, times(1)).persistUsage(commandCaptor.capture());
        AiUsageLogCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.userId()).isEqualTo(user.getId());
        assertThat(capturedCommand.contentId()).isEqualTo(content.getId());
        assertThat(capturedCommand.promptTokens()).isEqualTo(120);
        assertThat(capturedCommand.responseTokens()).isEqualTo(250);
        assertThat(capturedCommand.totalTokens()).isEqualTo(370);
        assertThat(capturedCommand.modelName()).isEqualTo("gemini-3-flash");
    }

    @Test
    void logUsage_NullTokenValues_StillDelegatesCommandWithNullsForWriterNormalization() {
        // Given
        User user = createUser();
        Content content = createContent();

        // When
        aiUsageLogService.logUsage(user, content, null, null, null, "gemini-3-flash");

        // Then
        verify(aiUsageLogAsyncWriter, times(1)).persistUsage(commandCaptor.capture());
        AiUsageLogCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.promptTokens()).isNull();
        assertThat(capturedCommand.responseTokens()).isNull();
        assertThat(capturedCommand.totalTokens()).isNull();
    }

    @Test
    void logUsage_UserIdMissing_SkipsLogging() {
        // Given
        User user = User.builder()
                .email("tester@example.com")
                .passwordHash("encoded-password")
                .fullName("Test User")
                .status(AccountStatus.ACTIVE)
                .build();
        Content content = createContent();

        // When
        aiUsageLogService.logUsage(user, content, 120, 250, 370, "gemini-3-flash");

        // Then
        verify(aiUsageLogAsyncWriter, never()).persistUsage(commandCaptor.capture());
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
