package com.farmtourism.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farmtourism.assistant.backend.FarmAssistantBackend
import com.farmtourism.assistant.backend.classifier.OnnxIntentClassifier
import com.farmtourism.assistant.backend.database.LocalFarmDatabase
import com.farmtourism.assistant.backend.mlkit.MlKitLanguageIdentifier
import com.farmtourism.assistant.backend.mlkit.MlKitTranslationEngine
import com.farmtourism.assistant.backend.model.FarmerProfile
import com.farmtourism.assistant.backend.model.IntentTemplate
import com.farmtourism.assistant.backend.model.MessageSender
import com.farmtourism.assistant.backend.model.PipelineTier
import com.farmtourism.assistant.backend.model.UnifiedTouristResult
import com.farmtourism.assistant.backend.pipeline.Tier2PromptRequest
import com.farmtourism.assistant.backend.pipeline.TouristTurnResult
import com.farmtourism.assistant.ui.model.UiChatMessage
import com.farmtourism.assistant.ui.navigation.AppMode
import com.farmtourism.assistant.backend.review.demo.DemoReviewScenarios
import com.farmtourism.assistant.backend.review.model.DemoReviewScenario
import com.farmtourism.assistant.backend.review.model.ReviewAnalysisResult
import com.farmtourism.assistant.backend.review.model.SubmittedReview
import com.farmtourism.assistant.ui.navigation.FarmerTab
import com.farmtourism.assistant.ui.navigation.TouristTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FarmAssistantUiState(
    val currentMode: AppMode = AppMode.TOURIST,
    val currentTouristTab: TouristTab = TouristTab.CHAT,
    val currentFarmerTab: FarmerTab = FarmerTab.INBOX,
    val isDarkTheme: Boolean = false,
    val isProcessing: Boolean = false,
    val isNoorSubmitting: Boolean = false,
    val touristWaitingForReply: Boolean = false,
    val selectedTouristLanguage: String = "auto", // "auto" or BCP-47 (e.g. "es", "fr", "de")
    val messages: List<UiChatMessage> = emptyList(),
    val pendingTier2Request: Tier2PromptRequest? = null,
    val pendingTier3FarmerPrompt: TouristTurnResult? = null,
    val farmerProfile: FarmerProfile = FarmerProfile(),
    val databaseSlots: Map<String, String> = emptyMap(),
    val templates: List<IntentTemplate> = emptyList(),
    val showSettingsSheet: Boolean = false,
    // Dedicated Reviews Section State
    val submittedReviews: List<SubmittedReview> = emptyList(),
    val selectedReviewId: String? = null,
    val isSubmittingReview: Boolean = false,
    val reviewSubmissionSuccess: Boolean = false,
    val reviewInputText: String = "",
    val reviewAuthorInput: String = "Visiting Tourist",
    val selectedDemoScenarioId: String? = null,
    val lastError: String? = null
) {
    val pendingInquiriesCount: Int
        get() = (if (pendingTier2Request != null) 1 else 0) + (if (pendingTier3FarmerPrompt != null) 1 else 0)

    val selectedReview: SubmittedReview?
        get() = submittedReviews.find { it.id == selectedReviewId } ?: submittedReviews.firstOrNull()
}

class FarmAssistantViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = LocalFarmDatabase(context = application.applicationContext)
    private val translationEngine = MlKitTranslationEngine()
    private val languageIdentifier = MlKitLanguageIdentifier()
    private val intentClassifier = OnnxIntentClassifier(context = application.applicationContext)

    private val backend = FarmAssistantBackend(
        farmerProfile = FarmerProfile(),
        translationEngine = translationEngine,
        languageIdentifier = languageIdentifier,
        intentClassifier = intentClassifier,
        database = database
    )

    private val _uiState = MutableStateFlow(FarmAssistantUiState())
    val uiState: StateFlow<FarmAssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { backend.initialize() }
            refreshDatabaseState()
            seedInitialDemoReviews()
        }
    }

    fun switchMode(mode: AppMode) {
        _uiState.update { it.copy(currentMode = mode) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun setTouristLanguage(languageCode: String) {
        _uiState.update { it.copy(selectedTouristLanguage = languageCode) }
    }

    fun toggleSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun dismissError() {
        _uiState.update { it.copy(lastError = null) }
    }

    /**
     * Tourist sends a question in any foreign language (Spanish, French, German, Italian, etc.)
     */
    fun sendTouristMessage(message: String, forcedLang: String? = null) {
        if (message.isBlank()) return

        val langToUse = forcedLang ?: if (_uiState.value.selectedTouristLanguage == "auto") null else _uiState.value.selectedTouristLanguage

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, lastError = null) }

            // Pre-add tourist bubble
            val touristBubble = UiChatMessage(
                sender = MessageSender.TOURIST,
                touristText = message,
                noorText = "पर्यटक का सवाल: \"$message\"",
                touristLanguage = langToUse ?: "auto"
            )
            _uiState.update { it.copy(messages = it.messages + touristBubble) }

            val result = backend.processTouristMessage(message, langToUse)

            result.onSuccess { touristResult ->
                when (touristResult) {
                    is UnifiedTouristResult.Tier1Hit -> {
                        val fastResult = touristResult.result
                        val replyBubble = UiChatMessage(
                            sender = MessageSender.FARMER,
                            touristText = fastResult.responseInTouristLanguage,
                            noorText = "⚡ ऑटो-जवाब (डेटाबेस): ${fastResult.englishReply}",
                            touristLanguage = fastResult.touristLanguage,
                            tier = PipelineTier.TIER_1_FAST_DB,
                            isAutoReply = true,
                            latencyMs = fastResult.latencyMs
                        )
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                touristWaitingForReply = false,
                                messages = it.messages + replyBubble,
                                pendingTier2Request = null,
                                pendingTier3FarmerPrompt = null
                            )
                        }
                    }
                    is UnifiedTouristResult.Tier2PromptRequired -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                touristWaitingForReply = true,
                                pendingTier2Request = touristResult.promptRequest,
                                pendingTier3FarmerPrompt = null
                            )
                        }
                    }
                    is UnifiedTouristResult.Tier3Fallback -> {
                        val fallback = touristResult.result
                        // Update tourist bubble with Hindi translation for Noor's side
                        _uiState.update { state ->
                            val updatedMessages = state.messages.map { msg ->
                                if (msg.id == touristBubble.id) {
                                    msg.copy(noorText = "पर्यटक (हिंदी में अनुवाद): \"${fallback.farmerTranslatedText}\"")
                                } else msg
                            }
                            state.copy(
                                isProcessing = false,
                                touristWaitingForReply = true,
                                messages = updatedMessages,
                                pendingTier3FarmerPrompt = fallback,
                                pendingTier2Request = null
                            )
                        }
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        lastError = err.localizedMessage ?: "Failed to process message on-device"
                    )
                }
            }
        }
    }

    /**
     * Noor provides the missing atomic value for Tier 2 (e.g. ₹500 or 9am-6pm)
     */
    fun answerTier2(enteredValue: String) {
        val request = _uiState.value.pendingTier2Request ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isNoorSubmitting = true, lastError = null) }
            val result = backend.completeTier2Prompt(request, enteredValue)

            result.onSuccess { completion ->
                refreshDatabaseState()
                val replyBubble = UiChatMessage(
                    sender = MessageSender.FARMER,
                    touristText = completion.responseInTouristLanguage,
                    noorText = "नूर: $enteredValue",
                    touristLanguage = completion.touristLanguage,
                    tier = PipelineTier.TIER_2_TEMPLATE_PROMPT,
                    latencyMs = completion.latencyMs
                )
                _uiState.update {
                    it.copy(
                        isNoorSubmitting = false,
                        touristWaitingForReply = false,
                        pendingTier2Request = null,
                        messages = it.messages + replyBubble
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isNoorSubmitting = false,
                        lastError = err.localizedMessage ?: "Failed to save answer"
                    )
                }
            }
        }
    }

    /**
     * Noor replies freely in Hindi for Tier 3
     */
    fun answerTier3(replyText: String) {
        val pending = _uiState.value.pendingTier3FarmerPrompt ?: return
        if (replyText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isNoorSubmitting = true, lastError = null) }
            val result = backend.handleFarmerReply(replyText, pending.touristLanguage)

            result.onSuccess { turnResult ->
                val replyBubble = UiChatMessage(
                    sender = MessageSender.FARMER,
                    touristText = turnResult.touristTranslatedText,
                    noorText = "नूर: $replyText",
                    touristLanguage = turnResult.touristLanguage,
                    tier = PipelineTier.TIER_3_DIRECT_TRANSLATION_FALLBACK,
                    latencyMs = turnResult.latencyMs
                )
                _uiState.update {
                    it.copy(
                        isNoorSubmitting = false,
                        touristWaitingForReply = false,
                        pendingTier3FarmerPrompt = null,
                        messages = it.messages + replyBubble
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isNoorSubmitting = false,
                        lastError = err.localizedMessage ?: "Failed to translate and send reply"
                    )
                }
            }
        }
    }

    fun dismissPendingTier2() {
        _uiState.update { it.copy(pendingTier2Request = null, touristWaitingForReply = false) }
    }

    fun dismissPendingTier3() {
        _uiState.update { it.copy(pendingTier3FarmerPrompt = null, touristWaitingForReply = false) }
    }

    fun updateDatabaseSlot(slotKey: String, slotValue: String) {
        viewModelScope.launch {
            backend.setDatabaseSlot(slotKey, slotValue)
            refreshDatabaseState()
        }
    }

    fun resetDatabaseDefaults() {
        viewModelScope.launch {
            backend.getDatabase().clear()
            refreshDatabaseState()
        }
    }

    fun clearChat() {
        backend.clearHistory()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                pendingTier2Request = null,
                pendingTier3FarmerPrompt = null,
                touristWaitingForReply = false,
                lastError = null
            )
        }
    }

    // Tab Navigation within Modes
    fun switchTouristTab(tab: TouristTab) {
        _uiState.update { it.copy(currentTouristTab = tab, reviewSubmissionSuccess = false) }
    }

    fun switchFarmerTab(tab: FarmerTab) {
        _uiState.update { it.copy(currentFarmerTab = tab) }
    }

    // Review Operations (Separate Section)
    fun setReviewInputText(text: String) {
        _uiState.update { it.copy(reviewInputText = text, reviewSubmissionSuccess = false) }
    }

    fun setReviewAuthorInput(author: String) {
        _uiState.update { it.copy(reviewAuthorInput = author) }
    }

    fun selectReview(reviewId: String) {
        _uiState.update { it.copy(selectedReviewId = reviewId) }
    }

    fun selectDemoScenario(scenario: DemoReviewScenario) {
        _uiState.update {
            it.copy(
                reviewInputText = scenario.originalReview,
                reviewAuthorInput = "Tourist (${scenario.visitorLanguageName})",
                selectedDemoScenarioId = scenario.id,
                reviewSubmissionSuccess = false,
                lastError = null
            )
        }
    }

    fun submitTouristReview(text: String? = null, author: String? = null) {
        val reviewText = (text ?: _uiState.value.reviewInputText).trim()
        val authorName = (author ?: _uiState.value.reviewAuthorInput).trim().ifBlank { "Visiting Tourist" }
        if (reviewText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReview = true, lastError = null) }
            val outcome = backend.analyzeReview(reviewText)
            val analysis = outcome.getOrNull()

            val newReview = SubmittedReview(
                author = authorName,
                originalText = reviewText,
                analysisResult = analysis
            )

            _uiState.update {
                it.copy(
                    isSubmittingReview = false,
                    reviewSubmissionSuccess = true,
                    submittedReviews = listOf(newReview) + it.submittedReviews,
                    selectedReviewId = newReview.id,
                    reviewInputText = "",
                    selectedDemoScenarioId = null
                )
            }
        }
    }

    fun toggleAcceptSuggestion(reviewId: String, aspect: String) {
        _uiState.update { state ->
            val updated = state.submittedReviews.map { review ->
                if (review.id == reviewId) {
                    val currentAccepted = review.acceptedSuggestions
                    val newAccepted = if (currentAccepted.contains(aspect)) {
                        currentAccepted - aspect
                    } else {
                        currentAccepted + aspect
                    }
                    review.copy(acceptedSuggestions = newAccepted)
                } else review
            }
            state.copy(submittedReviews = updated)
        }
    }

    fun dismissReviewSuccessMessage() {
        _uiState.update { it.copy(reviewSubmissionSuccess = false) }
    }

    private suspend fun seedInitialDemoReviews() {
        val initialList = mutableListOf<SubmittedReview>()
        for (scenario in DemoReviewScenarios.SCENARIOS) {
            val outcome = backend.analyzeReview(scenario.originalReview)
            initialList.add(
                SubmittedReview(
                    id = scenario.id,
                    author = "Visitor (${scenario.visitorLanguageName})",
                    originalText = scenario.originalReview,
                    analysisResult = outcome.getOrNull()
                )
            )
        }
        _uiState.update {
            it.copy(
                submittedReviews = initialList,
                selectedReviewId = initialList.firstOrNull()?.id
            )
        }
    }

    private suspend fun refreshDatabaseState() {
        val slots = backend.getAllDatabaseSlots()
        val templates = backend.getDatabase().getAllTemplates()
        _uiState.update {
            it.copy(
                databaseSlots = slots,
                templates = templates
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        backend.shutdown()
    }
}
