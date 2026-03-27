package com.portfolio.aicontentstudio.modules.ai_log.service;

import com.portfolio.aicontentstudio.modules.ai_log.entity.AiUsageLog;
import com.portfolio.aicontentstudio.modules.ai_log.repository.AiUsageLogRepository;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiUsageLogAsyncWriterTest {

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private AiUsageLogAsyncWriter aiUsageLogAsyncWriter;

    @Captor
    private ArgumentCaptor<AiUsageLog> usageLogCaptor;

    @Test
    void persistUsage_AllValuesPresent_SavesUsageLog() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        User user = createUser(userId);
        Content content = createContent(contentId);
        AiUsageLogCommand command = new AiUsageLogCommand(userId, contentId, 120, 250, 370, "gemini-3-flash");

        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(contentRepository.getReferenceById(contentId)).willReturn(content);

        // When
        aiUsageLogAsyncWriter.persistUsage(command);

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
    void persistUsage_NullTokenValues_DefaultsMissingValuesToZero() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        User user = createUser(userId);
        Content content = createContent(contentId);
        AiUsageLogCommand command = new AiUsageLogCommand(userId, contentId, null, null, null, "gemini-3-flash");

        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(contentRepository.getReferenceById(contentId)).willReturn(content);

        // When
        aiUsageLogAsyncWriter.persistUsage(command);

        // Then
        verify(aiUsageLogRepository, times(1)).save(usageLogCaptor.capture());
        AiUsageLog capturedLog = usageLogCaptor.getValue();
        assertThat(capturedLog.getPromptTokens()).isZero();
        assertThat(capturedLog.getResponseTokens()).isZero();
        assertThat(capturedLog.getTotalTokens()).isZero();
    }

    @Test
    void persistUsage_TotalTokensMissing_FallsBackToPromptPlusResponse() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        User user = createUser(userId);
        Content content = createContent(contentId);
        AiUsageLogCommand command = new AiUsageLogCommand(userId, contentId, 1192, 679, null, "gemini-3-flash");

        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(contentRepository.getReferenceById(contentId)).willReturn(content);

        // When
        aiUsageLogAsyncWriter.persistUsage(command);

        // Then
        verify(aiUsageLogRepository, times(1)).save(usageLogCaptor.capture());
        AiUsageLog capturedLog = usageLogCaptor.getValue();
        assertThat(capturedLog.getPromptTokens()).isEqualTo(1192);
        assertThat(capturedLog.getResponseTokens()).isEqualTo(679);
        assertThat(capturedLog.getTotalTokens()).isEqualTo(1871);
    }

    @Test
    void persistUsage_RepositoryThrowsException_DoesNotPropagateException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        User user = createUser(userId);
        Content content = createContent(contentId);
        AiUsageLogCommand command = new AiUsageLogCommand(userId, contentId, 120, 250, 370, "gemini-3-flash");

        given(userRepository.getReferenceById(userId)).willReturn(user);
        given(contentRepository.getReferenceById(contentId)).willReturn(content);
        given(aiUsageLogRepository.save(any(AiUsageLog.class))).willThrow(new RuntimeException("DB error"));

        // When
        // Then
        assertThatCode(() -> aiUsageLogAsyncWriter.persistUsage(command))
                .doesNotThrowAnyException();
    }

    private User createUser(UUID userId) {
        User user = User.builder()
                .email("tester@example.com")
                .passwordHash("encoded-password")
                .fullName("Test User")
                .status(AccountStatus.ACTIVE)
                .build();
        user.setId(userId);
        return user;
    }

    private Content createContent(UUID contentId) {
        Content content = Content.builder().build();
        content.setId(contentId);
        return content;
    }
}
