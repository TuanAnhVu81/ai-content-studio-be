package com.portfolio.aicontentstudio.modules.content.service;

import com.portfolio.aicontentstudio.core.constant.ErrorCode;
import com.portfolio.aicontentstudio.core.exception.AppException;
import com.portfolio.aicontentstudio.modules.ai_log.service.AiUsageLogService;
import com.portfolio.aicontentstudio.modules.campaign.entity.Campaign;
import com.portfolio.aicontentstudio.modules.campaign.repository.CampaignRepository;
import com.portfolio.aicontentstudio.modules.content.dto.ContentResponse;
import com.portfolio.aicontentstudio.modules.content.dto.GenerateContentRequest;
import com.portfolio.aicontentstudio.modules.content.dto.PromptConfig;
import com.portfolio.aicontentstudio.modules.content.dto.SeoMetadata;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateBannerRequest;
import com.portfolio.aicontentstudio.modules.content.dto.UpdateContentRequest;
import com.portfolio.aicontentstudio.modules.content.entity.Content;
import com.portfolio.aicontentstudio.modules.content.entity.ContentStatus;
import com.portfolio.aicontentstudio.modules.content.mapper.ContentMapper;
import com.portfolio.aicontentstudio.modules.content.repository.ContentRepository;
import com.portfolio.aicontentstudio.modules.user.entity.AccountStatus;
import com.portfolio.aicontentstudio.modules.user.entity.User;
import com.portfolio.aicontentstudio.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pure Unit Test for ContentServiceImpl using JUnit 5, Mockito, and AssertJ.
 * Covers Phase 5 core AI generation, content CRUD, SEO sanity checks, and IDOR protection.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private ChatModel chatModel;

    @Mock
    private AiUsageLogService aiUsageLogService;

    @InjectMocks
    private ContentServiceImpl contentService;

    @Captor
    private ArgumentCaptor<Content> contentCaptor;

    @Captor
    private ArgumentCaptor<Prompt> promptCaptor;

    @BeforeEach
    void setUp() {
        setField(contentService, "configuredModelName", "gemini-test-model");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: generateContent(GenerateContentRequest request, UUID userId)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void generateContent_ValidRequestWithMetadataUsage_SavesContentAndReturnsResponse() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, 150, "Vietnamese");
        User user = createUser(userId);
        Campaign campaign = createCampaign(campaignId, userId);
        ContentResponse expectedResponse = createContentResponse(contentId, campaignId, request.keyword(), request.language(), null, null);
        ChatResponseMetadata metadata = createChatResponseMetadata("gemini-3-flash", 120, 250, 370);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.of(campaign));
        given(chatModel.call(any(Prompt.class))).willReturn(createChatResponse("  Generated content body.  ", metadata));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> {
            Content captured = invocation.getArgument(0);
            captured.setId(contentId);
            return captured;
        });
        given(contentMapper.toResponse(any(Content.class))).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.generateContent(request, userId);

        // Then
        verify(chatModel, times(1)).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt.getSystemMessage().getText())
                .contains("Senior SEO Copywriter");
        assertThat(capturedPrompt.getUserMessage().getText())
                .contains("Platform: Facebook")
                .contains("Tone: Friendly")
                .contains("Main keyword: student laptop deal")
                .contains("Length: 150 words")
                .contains("Language: Vietnamese");

        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getCampaign()).isEqualTo(campaign);
        assertThat(capturedContent.getUser()).isEqualTo(user);
        assertThat(capturedContent.getTargetKeyword()).isEqualTo(request.keyword());
        assertThat(capturedContent.getGeneratedText()).isEqualTo("Generated content body.");
        assertThat(capturedContent.getSeoMetadata()).isNull();
        assertThat(capturedContent.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(capturedContent.getPromptConfig()).isEqualTo(new PromptConfig("Facebook", "Friendly", "150", "Vietnamese"));

        verify(aiUsageLogService, times(1)).logUsage(user, capturedContent, 120, 250, 370, "gemini-3-flash");
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void generateContent_MetadataMissingUsageAndModel_FallsBackToConfiguredModel() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, null, "English");
        User user = createUser(userId);
        Campaign campaign = createCampaign(campaignId, userId);
        ContentResponse expectedResponse = createContentResponse(contentId, campaignId, request.keyword(), request.language(), null, null);

        ChatResponseMetadata metadata = org.mockito.Mockito.mock(ChatResponseMetadata.class);
        given(metadata.getUsage()).willReturn(null);
        given(metadata.getModel()).willReturn(" ");

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.of(campaign));
        given(chatModel.call(any(Prompt.class))).willReturn(createChatResponse("Generated content body.", metadata));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> {
            Content captured = invocation.getArgument(0);
            captured.setId(contentId);
            return captured;
        });
        given(contentMapper.toResponse(any(Content.class))).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.generateContent(request, userId);

        // Then
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getPromptConfig()).isEqualTo(new PromptConfig("Facebook", "Friendly", "auto", "English"));

        verify(aiUsageLogService, times(1)).logUsage(user, capturedContent, null, null, null, "gemini-test-model");
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void generateContent_UserNotFound_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(UUID.randomUUID(), 150, "Vietnamese");
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> contentService.generateContent(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(campaignRepository, never()).findByIdAndUserId(any(UUID.class), any(UUID.class));
        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void generateContent_CampaignNotFound_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, 150, "Vietnamese");
        given(userRepository.findById(userId)).willReturn(Optional.of(createUser(userId)));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> contentService.generateContent(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAMPAIGN_NOT_FOUND);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void generateContent_ChatModelThrows_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, 150, "Vietnamese");
        User user = createUser(userId);
        Campaign campaign = createCampaign(campaignId, userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.of(campaign));
        given(chatModel.call(any(Prompt.class))).willThrow(new RuntimeException("Gemini unavailable"));

        // When
        // Then
        assertThatThrownBy(() -> contentService.generateContent(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);

        verify(contentRepository, never()).save(any(Content.class));
        verify(aiUsageLogService, never()).logUsage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateContent_ChatResponseNull_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, 150, "Vietnamese");
        User user = createUser(userId);
        Campaign campaign = createCampaign(campaignId, userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.of(campaign));
        given(chatModel.call(any(Prompt.class))).willReturn(null);

        // When
        // Then
        assertThatThrownBy(() -> contentService.generateContent(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);

        verify(contentRepository, never()).save(any(Content.class));
        verify(aiUsageLogService, never()).logUsage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateContent_BlankGeneratedText_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        GenerateContentRequest request = createGenerateContentRequest(campaignId, 150, "Vietnamese");
        User user = createUser(userId);
        Campaign campaign = createCampaign(campaignId, userId);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(campaignRepository.findByIdAndUserId(campaignId, userId)).willReturn(Optional.of(campaign));
        given(chatModel.call(any(Prompt.class))).willReturn(createChatResponse("   ", null));

        // When
        // Then
        assertThatThrownBy(() -> contentService.generateContent(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);

        verify(contentRepository, never()).save(any(Content.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getContentsByCampaign(UUID campaignId, UUID userId, Pageable pageable)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getContentsByCampaign_CampaignOwned_ReturnsMappedPage() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Content content = createContent(UUID.randomUUID(), campaignId, userId);
        ContentResponse expectedResponse = createContentResponse(content.getId(), campaignId, content.getTargetKeyword(), "Vietnamese", content.getSeoMetadata(), content.getBannerUrl());
        Page<Content> contentPage = new PageImpl<>(List.of(content));

        given(campaignRepository.existsByIdAndUserId(campaignId, userId)).willReturn(true);
        given(contentRepository.findAllByCampaignIdAndUserId(campaignId, userId, pageable)).willReturn(contentPage);
        given(contentMapper.toResponse(content)).willReturn(expectedResponse);

        // When
        Page<ContentResponse> actualResponse = contentService.getContentsByCampaign(campaignId, userId, pageable);

        // Then
        assertThat(actualResponse.getContent()).containsExactly(expectedResponse);
        verify(contentRepository, times(1)).findAllByCampaignIdAndUserId(campaignId, userId, pageable);
    }

    @Test
    void getContentsByCampaign_CampaignNotOwned_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        given(campaignRepository.existsByIdAndUserId(campaignId, userId)).willReturn(false);

        // When
        // Then
        assertThatThrownBy(() -> contentService.getContentsByCampaign(campaignId, userId, pageable))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CAMPAIGN_NOT_FOUND);

        verify(contentRepository, never()).findAllByCampaignIdAndUserId(any(UUID.class), any(UUID.class), any(Pageable.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: getContentById(UUID id, UUID userId)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void getContentById_ContentExists_ReturnsMappedResponse() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Content content = createContent(UUID.randomUUID(), campaignId, userId);
        ContentResponse expectedResponse = createContentResponse(content.getId(), campaignId, content.getTargetKeyword(), "Vietnamese", content.getSeoMetadata(), content.getBannerUrl());

        given(contentRepository.findByIdAndUserId(content.getId(), userId)).willReturn(Optional.of(content));
        given(contentMapper.toResponse(content)).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.getContentById(content.getId(), userId);

        // Then
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void getContentById_ContentMissing_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        given(contentRepository.findByIdAndUserId(contentId, userId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> contentService.getContentById(contentId, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENT_NOT_FOUND);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: updateContent(UUID id, UpdateContentRequest request, UUID userId)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void updateContent_HighScoreWithoutH1_CapsScoreToSixty() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createContent(contentId, campaignId, userId);
        UpdateContentRequest request = new UpdateContentRequest(
                "Updated generated text",
                new SeoMetadata(95.0, 2.1, false, false, 0, null, null, false, false, List.of("Add an H1 heading"))
        );
        ContentResponse expectedResponse = createContentResponse(contentId, campaignId, content.getTargetKeyword(), "Vietnamese", new SeoMetadata(60.0, 2.1, false, false, 0, null, null, false, false, List.of("Add an H1 heading")), null);

        given(contentRepository.findByIdAndUserId(contentId, userId)).willReturn(Optional.of(content));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(contentMapper.toResponse(any(Content.class))).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.updateContent(contentId, request, userId);

        // Then
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getGeneratedText()).isEqualTo("Updated generated text");
        assertThat(capturedContent.getSeoMetadata()).isEqualTo(new SeoMetadata(60.0, 2.1, false, false, 0, null, null, false, false, List.of("Add an H1 heading")));
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void updateContent_NullSeoMetadata_PersistsNullSeoMetadata() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createContent(contentId, campaignId, userId);
        UpdateContentRequest request = new UpdateContentRequest("Updated generated text", null);
        ContentResponse expectedResponse = createContentResponse(contentId, campaignId, content.getTargetKeyword(), "Vietnamese", null, null);

        given(contentRepository.findByIdAndUserId(contentId, userId)).willReturn(Optional.of(content));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(contentMapper.toResponse(any(Content.class))).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.updateContent(contentId, request, userId);

        // Then
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getSeoMetadata()).isNull();
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: updateBanner(UUID id, UpdateBannerRequest request, UUID userId)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void updateBanner_ContentExists_UpdatesBannerUrl() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createContent(contentId, campaignId, userId);
        UpdateBannerRequest request = new UpdateBannerRequest("https://cdn.example.com/banner.jpg");
        ContentResponse expectedResponse = createContentResponse(contentId, campaignId, content.getTargetKeyword(), "Vietnamese", content.getSeoMetadata(), request.bannerUrl());

        given(contentRepository.findByIdAndUserId(contentId, userId)).willReturn(Optional.of(content));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(contentMapper.toResponse(any(Content.class))).willReturn(expectedResponse);

        // When
        ContentResponse actualResponse = contentService.updateBanner(contentId, request, userId);

        // Then
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getBannerUrl()).isEqualTo("https://cdn.example.com/banner.jpg");
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // TEST CASES: deleteContent(UUID id, UUID userId)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void deleteContent_ContentNotFound_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        given(contentRepository.findById(contentId)).willReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> contentService.deleteContent(contentId, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENT_NOT_FOUND);

        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void deleteContent_ContentOwnedByDifferentUser_ThrowsAppException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createContent(contentId, campaignId, ownerUserId);
        given(contentRepository.findById(contentId)).willReturn(Optional.of(content));

        // When
        // Then
        assertThatThrownBy(() -> contentService.deleteContent(contentId, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENT_NOT_FOUND);

        verify(contentRepository, never()).save(any(Content.class));
    }

    @Test
    void deleteContent_ContentOwnedByUser_SetsDeletedAtAndSaves() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Content content = createContent(contentId, campaignId, userId);

        given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
        given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));

        // When
        contentService.deleteContent(contentId, userId);

        // Then
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.getDeletedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DATA GENERATORS
    // -----------------------------------------------------------------------------------------------------------------

    private GenerateContentRequest createGenerateContentRequest(UUID campaignId, Integer lengthLimit, String language) {
        return new GenerateContentRequest(campaignId, "Facebook", "Friendly", "student laptop deal", lengthLimit, language);
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

    private Campaign createCampaign(UUID campaignId, UUID userId) {
        Campaign campaign = Campaign.builder()
                .name("Back to School 2026")
                .userId(userId)
                .build();
        campaign.setId(campaignId);
        return campaign;
    }

    private Content createContent(UUID contentId, UUID campaignId, UUID userId) {
        Content content = Content.builder()
                .campaign(createCampaign(campaignId, userId))
                .user(createUser(userId))
                .targetKeyword("student laptop deal")
                .promptConfig(new PromptConfig("Facebook", "Friendly", "150", "Vietnamese"))
                .generatedText("Existing generated text")
                .seoMetadata(new SeoMetadata(70.0, 1.8, true, false, 0, null, null, false, false, List.of("Add one more heading")))
                .status(ContentStatus.DRAFT)
                .build();
        content.setId(contentId);
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        return content;
    }

    private ContentResponse createContentResponse(UUID contentId, UUID campaignId, String keyword, String language, SeoMetadata seoMetadata, String bannerUrl) {
        return new ContentResponse(
                contentId,
                campaignId,
                "Campaign Alpha",
                keyword,
                new PromptConfig("Facebook", "Friendly", "150", language),
                "Mapped content response",
                seoMetadata,
                bannerUrl,
                ContentStatus.DRAFT,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private ChatResponse createChatResponse(String text, ChatResponseMetadata metadata) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    private ChatResponseMetadata createChatResponseMetadata(String modelName, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        Usage usage = org.mockito.Mockito.mock(Usage.class);
        ChatResponseMetadata metadata = org.mockito.Mockito.mock(ChatResponseMetadata.class);

        given(usage.getPromptTokens()).willReturn(promptTokens);
        given(usage.getCompletionTokens()).willReturn(completionTokens);
        given(usage.getTotalTokens()).willReturn(totalTokens);
        given(metadata.getUsage()).willReturn(usage);
        given(metadata.getModel()).willReturn(modelName);

        return metadata;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field for test setup", ex);
        }
    }
}
