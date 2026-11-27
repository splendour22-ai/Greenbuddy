package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BuddyAiResult
import com.example.data.BuddyAiService
import com.example.data.ChartCardData
import com.example.data.ChatMessage
import com.example.data.ChatSender
import com.example.data.CustomRecordType
import com.example.data.FarmActivity
import com.example.data.FarmAnalyticsSummary
import com.example.data.FarmProfile
import com.example.data.FarmRepository
import com.example.data.LearnedMemory
import com.example.data.ProposalStatus
import com.example.data.ProposedActivityDraft
import com.example.data.ProposedCustomTypeDraft
import com.example.data.ProposedMemoryDraft
import com.example.data.RecordType
import com.example.data.formatMoney
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

private const val TAG = "BuddyViewModel"

sealed interface DataStreamState<out T> {
    data object Loading : DataStreamState<Nothing>
    data class Success<T>(val data: T) : DataStreamState<T>
    data class Error(val message: String) : DataStreamState<Nothing>
}

data class SessionCardAttachments(
    val activityDraft: ProposedActivityDraft? = null,
    val memoryDraft: ProposedMemoryDraft? = null,
    val customTypeDraft: ProposedCustomTypeDraft? = null,
    val chartData: ChartCardData? = null
)

data class BuddyScreenState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAiThinking: Boolean = false,
    val profile: FarmProfile = FarmProfile(),
    val activities: List<FarmActivity> = emptyList(),
    val memories: List<LearnedMemory> = emptyList(),
    val customTypes: List<CustomRecordType> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val analytics: FarmAnalyticsSummary = FarmAnalyticsSummary(
        totalRevenue = 0.0,
        totalExpenses = 0.0,
        netProfit = 0.0,
        totalUnpaidReceivables = 0.0,
        totalHarvestUnits = emptyMap(),
        cropRevenueMap = emptyMap(),
        debtorBalances = emptyMap(),
        monthlyNetMap = emptyMap(),
        activityCount = 0
    ),
    val attachmentsByMessageId: Map<String, SessionCardAttachments> = emptyMap(),
    val quickReplies: List<String> = emptyList(),
    val dailyCheckInHeadline: String = "",
    val dailyPulseTip: String? = null,
    val toastMessage: String? = null
)

class BuddyViewModel(
    private val repository: FarmRepository,
    private val aiService: BuddyAiService,
    private val currentUserId: String,
    private val defaultOwnerName: String
) : ViewModel() {

    private val _attachments = MutableStateFlow<Map<String, SessionCardAttachments>>(emptyMap())
    private val _quickReplies = MutableStateFlow<List<String>>(emptyList())
    private val _isAiThinking = MutableStateFlow(false)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private var initialGreetingChecked = false

    private val profileFlow: StateFlow<DataStreamState<FarmProfile?>> =
        repository.observeProfile(currentUserId)
            .mapToUiState("Failed to load farm profile")

    private val activitiesFlow: StateFlow<DataStreamState<List<FarmActivity>>> =
        repository.observeActivities(currentUserId)
            .mapToUiState("Failed to load farm records")

    private val memoriesFlow: StateFlow<DataStreamState<List<LearnedMemory>>> =
        repository.observeMemories(currentUserId)
            .mapToUiState("Failed to load farm memory")

    private val customTypesFlow: StateFlow<DataStreamState<List<CustomRecordType>>> =
        repository.observeCustomRecordTypes(currentUserId)
            .mapToUiState("Failed to load custom sections")

    private val messagesFlow: StateFlow<DataStreamState<List<ChatMessage>>> =
        repository.observeChatMessages(currentUserId)
            .mapToUiState("Failed to load chat history")

    private val coreDataFlow = combine(
        profileFlow,
        activitiesFlow,
        memoriesFlow,
        customTypesFlow,
        messagesFlow
    ) { profState, actState, memState, typeState, msgState ->
        val firstError = listOf(profState, actState, memState, typeState, msgState)
            .filterIsInstance<DataStreamState.Error>()
            .firstOrNull()
        val anyLoading = listOf(profState, actState, memState, typeState, msgState)
            .any { it is DataStreamState.Loading }

        val profile = (profState as? DataStreamState.Success)?.data ?: FarmProfile(
            userId = currentUserId,
            ownerName = defaultOwnerName.ifBlank { "Farmer" }
        )
        val activities = (actState as? DataStreamState.Success)?.data ?: emptyList()
        val memories = (memState as? DataStreamState.Success)?.data ?: emptyList()
        val customTypes = (typeState as? DataStreamState.Success)?.data ?: emptyList()
        val messages = (msgState as? DataStreamState.Success)?.data ?: emptyList()

        val analytics = computeAnalytics(activities)
        val (headline, tip) = buildDailyGreetingAndTip(profile, activities, analytics)

        if (!anyLoading && firstError == null && !initialGreetingChecked) {
            initialGreetingChecked = true
            ensureWelcomeConversation(profile, messages)
        }

        BuddyScreenState(
            isLoading = anyLoading,
            errorMessage = firstError?.message,
            profile = profile,
            activities = activities,
            memories = memories,
            customTypes = customTypes,
            chatMessages = messages,
            analytics = analytics,
            dailyCheckInHeadline = headline,
            dailyPulseTip = tip
        )
    }

    val uiState: StateFlow<BuddyScreenState> = combine(
        coreDataFlow,
        _attachments.asStateFlow(),
        _quickReplies.asStateFlow(),
        _isAiThinking.asStateFlow(),
        _toastMessage.asStateFlow()
    ) { core, attachments, quickReplies, thinking, toast ->
        val fallbackReplies = if (quickReplies.isEmpty() && core.chatMessages.isNotEmpty()) {
            if (!core.profile.onboardingCompleted) {
                listOf("We grow maize & tomatoes", "Let's get started", "Skip setup for now")
            } else {
                listOf("Who owes me money?", "How much profit so far?", "Log today's harvest")
            }
        } else {
            quickReplies
        }
        core.copy(
            isAiThinking = thinking,
            attachmentsByMessageId = attachments,
            quickReplies = fallbackReplies,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BuddyScreenState()
    )

    init {
        viewModelScope.launch {
            val result = repository.ensureProfileInitialized(defaultOwnerName)
            result.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Couldn't initialize farm profile"
            }
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.mapToUiState(
        fallbackMessage: String
    ): StateFlow<DataStreamState<T>> {
        return kotlinx.coroutines.flow.flow {
            collect { emit(DataStreamState.Success(it) as DataStreamState<T>) }
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, fallbackMessage, rootError)
            emit(DataStreamState.Error(rootError.message ?: fallbackMessage))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataStreamState.Loading
        )
    }

    private fun ensureWelcomeConversation(profile: FarmProfile, messages: List<ChatMessage>) {
        if (messages.isNotEmpty()) return
        viewModelScope.launch {
            val firstName = profile.ownerName.split(" ").firstOrNull().orEmpty().ifBlank { "Friend" }
            val greeting = if (!profile.onboardingCompleted) {
                "Hey $firstName! I'm GreenHarvest Buddy, your new farm partner 🌱 I'd love to learn a bit about ${profile.farmName} so I can help keep your records tidy. First off—what do you mainly grow or rear on the farm?"
            } else {
                "${timeOfDayGreeting()} $firstName! Anything happening on ${profile.farmName} today?"
            }
            repository.addChatMessage(
                ChatMessage(
                    userId = currentUserId,
                    sender = ChatSender.BUDDY.name,
                    text = greeting
                )
            )
            _quickReplies.value = if (!profile.onboardingCompleted) {
                listOf("Maize & tomatoes", "Cassava & poultry", "Fish ponds & vegetables")
            } else {
                listOf("Log a sale", "Record a harvest", "Who owes me money?")
            }
        }
    }

    fun sendUserTextMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _isAiThinking.value) return
        sendTurnInternal(userText = text, audioBytes = null)
    }

    fun sendVoiceAudioClip(audioBytes: ByteArray, mimeType: String) {
        if (audioBytes.isEmpty() || _isAiThinking.value) return
        sendTurnInternal(userText = null, audioBytes = audioBytes, audioMimeType = mimeType)
    }

    private fun sendTurnInternal(
        userText: String?,
        audioBytes: ByteArray?,
        audioMimeType: String = "audio/mp4"
    ) {
        val snapshot = uiState.value
        _isAiThinking.value = true
        _quickReplies.value = emptyList()

        viewModelScope.launch {
            try {
                // Save typed user message right away if text was supplied
                if (!userText.isNullOrBlank()) {
                    repository.addChatMessage(
                        ChatMessage(
                            userId = currentUserId,
                            sender = ChatSender.USER.name,
                            text = userText
                        )
                    )
                }

                val aiResult = aiService.sendTurn(
                    userText = userText,
                    audioBytes = audioBytes,
                    audioMimeType = audioMimeType,
                    profile = snapshot.profile,
                    memories = snapshot.memories,
                    customTypes = snapshot.customTypes,
                    activities = snapshot.activities,
                    analytics = snapshot.analytics,
                    recentMessages = snapshot.chatMessages
                )

                aiResult.onSuccess { res ->
                    if (userText.isNullOrBlank() && !res.userTranscript.isNullOrBlank()) {
                        repository.addChatMessage(
                            ChatMessage(
                                userId = currentUserId,
                                sender = ChatSender.USER.name,
                                text = "🎤 ${res.userTranscript}"
                            )
                        )
                    }

                    // Apply profile step updates if onboarding progressed
                    if (res.nextOnboardingStep != null || res.updatedOwnerName != null ||
                        res.updatedFarmName != null || res.updatedCurrency != null
                    ) {
                        repository.updateProfile(
                            ownerName = res.updatedOwnerName,
                            farmName = res.updatedFarmName,
                            currencySymbol = res.updatedCurrency,
                            onboardingStep = res.nextOnboardingStep,
                            dailyGoalNote = res.updatedGoalNote
                        )
                    }

                    // Apply direct correction delete if user explicitly asked to remove a wrong entry
                    if (res.correctionAction == "DELETE_TARGET" && !res.correctionTargetId.isNullOrBlank()) {
                        repository.deleteActivity(res.correctionTargetId)
                    }

                    val msgResult = repository.addChatMessage(
                        ChatMessage(
                            userId = currentUserId,
                            sender = ChatSender.BUDDY.name,
                            text = res.replyText
                        )
                    )

                    msgResult.onSuccess { msgId ->
                        attachSessionCards(msgId, res)
                    }
                    _quickReplies.value = res.quickReplies
                }.onFailure { err ->
                    val errMsg = err.localizedMessage ?: "Something went wrong talking to Buddy."
                    _toastMessage.value = errMsg
                    repository.addChatMessage(
                        ChatMessage(
                            userId = currentUserId,
                            sender = ChatSender.BUDDY.name,
                            text = "Sorry, I had a quick hiccup reaching the server ($errMsg). Want to try that again?"
                        )
                    )
                }
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    private fun attachSessionCards(msgId: String, res: BuddyAiResult) {
        val draftId = UUID.randomUUID().toString()
        val activityDraft = if (res.proposedActivity != null && res.proposedCustomType == null) {
            ProposedActivityDraft(
                draftId = draftId,
                activity = res.proposedActivity,
                status = ProposalStatus.PENDING
            )
        } else null

        val memoryDraft = if (res.proposedMemories.isNotEmpty() || res.markOnboardingComplete) {
            ProposedMemoryDraft(
                draftId = "mem_$draftId",
                memories = res.proposedMemories,
                markOnboardingComplete = res.markOnboardingComplete,
                updatedOwnerName = res.updatedOwnerName,
                updatedFarmName = res.updatedFarmName,
                updatedCurrency = res.updatedCurrency,
                updatedGoalNote = res.updatedGoalNote,
                status = ProposalStatus.PENDING
            )
        } else null

        val customTypeDraft = res.proposedCustomType?.let { ct ->
            ProposedCustomTypeDraft(
                draftId = "ct_$draftId",
                customType = ct,
                initialActivity = res.proposedActivity,
                status = ProposalStatus.PENDING
            )
        }

        if (activityDraft != null || memoryDraft != null || customTypeDraft != null || res.chartData != null) {
            _attachments.update { current ->
                current + (msgId to SessionCardAttachments(
                    activityDraft = activityDraft,
                    memoryDraft = memoryDraft,
                    customTypeDraft = customTypeDraft,
                    chartData = res.chartData
                ))
            }
        }
    }

    fun confirmActivityDraft(messageId: String, confirmedActivity: FarmActivity) {
        viewModelScope.launch {
            val result = if (confirmedActivity.id.isNotBlank()) {
                repository.updateActivity(confirmedActivity).map { confirmedActivity.id }
            } else {
                repository.addActivity(confirmedActivity)
            }
            result.onSuccess { savedId ->
                val cur = uiState.value.profile.currencySymbol
                val summary = "${confirmedActivity.parsedRecordType.label}: ${confirmedActivity.title}" +
                    if (confirmedActivity.totalAmount > 0) " • ${formatMoney(confirmedActivity.totalAmount, cur)}" else ""
                repository.updateChatMessageCard(
                    messageId = messageId,
                    linkedActivityId = savedId,
                    cardSummary = summary
                )
                _attachments.update { map ->
                    val existing = map[messageId] ?: return@update map
                    map + (messageId to existing.copy(
                        activityDraft = existing.activityDraft?.copy(
                            activity = confirmedActivity.copy(id = savedId),
                            status = ProposalStatus.CONFIRMED,
                            savedActivityId = savedId
                        )
                    ))
                }
                _toastMessage.value = "Saved to your farm records!"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to save record"
            }
        }
    }

    fun dismissActivityDraft(messageId: String) {
        _attachments.update { map ->
            val existing = map[messageId] ?: return@update map
            map + (messageId to existing.copy(
                activityDraft = existing.activityDraft?.copy(status = ProposalStatus.DISMISSED)
            ))
        }
    }

    fun confirmMemoryDraft(messageId: String) {
        val draft = _attachments.value[messageId]?.memoryDraft ?: return
        viewModelScope.launch {
            draft.memories.forEach { mem ->
                repository.addMemory(mem)
            }
            if (draft.markOnboardingComplete || draft.updatedOwnerName != null ||
                draft.updatedFarmName != null || draft.updatedCurrency != null || draft.updatedGoalNote != null
            ) {
                repository.updateProfile(
                    ownerName = draft.updatedOwnerName,
                    farmName = draft.updatedFarmName,
                    currencySymbol = draft.updatedCurrency,
                    onboardingCompleted = if (draft.markOnboardingComplete) true else null,
                    onboardingStep = if (draft.markOnboardingComplete) 7L else null,
                    dailyGoalNote = draft.updatedGoalNote
                )
            }
            _attachments.update { map ->
                val existing = map[messageId] ?: return@update map
                map + (messageId to existing.copy(
                    memoryDraft = draft.copy(status = ProposalStatus.CONFIRMED)
                ))
            }
            _toastMessage.value = "Saved to Buddy's farm memory!"
        }
    }

    fun dismissMemoryDraft(messageId: String) {
        _attachments.update { map ->
            val existing = map[messageId] ?: return@update map
            val draft = existing.memoryDraft ?: return@update map
            map + (messageId to existing.copy(
                memoryDraft = draft.copy(status = ProposalStatus.DISMISSED)
            ))
        }
    }

    fun approveCustomTypeDraft(messageId: String) {
        val draft = _attachments.value[messageId]?.customTypeDraft ?: return
        viewModelScope.launch {
            repository.addCustomRecordType(draft.customType).onSuccess {
                var savedActId: String? = null
                draft.initialActivity?.let { act ->
                    val customAct = act.copy(
                        recordType = RecordType.CUSTOM.name,
                        customTypeName = draft.customType.name
                    )
                    repository.addActivity(customAct).onSuccess { id ->
                        savedActId = id
                        repository.updateChatMessageCard(
                            messageId = messageId,
                            linkedActivityId = id,
                            cardSummary = "${draft.customType.name}: ${customAct.title}"
                        )
                    }
                }
                _attachments.update { map ->
                    val existing = map[messageId] ?: return@update map
                    map + (messageId to existing.copy(
                        customTypeDraft = draft.copy(status = ProposalStatus.CONFIRMED)
                    ))
                }
                _toastMessage.value = if (savedActId != null) {
                    "Created '${draft.customType.name}' section and logged entry!"
                } else {
                    "Created '${draft.customType.name}' section!"
                }
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to create custom section"
            }
        }
    }

    fun dismissCustomTypeDraft(messageId: String) {
        _attachments.update { map ->
            val existing = map[messageId] ?: return@update map
            val draft = existing.customTypeDraft ?: return@update map
            map + (messageId to existing.copy(
                customTypeDraft = draft.copy(status = ProposalStatus.DISMISSED)
            ))
        }
    }

    fun saveOrUpdateActivity(activity: FarmActivity) {
        viewModelScope.launch {
            val result = if (activity.id.isBlank()) {
                repository.addActivity(activity).map { Unit }
            } else {
                repository.updateActivity(activity)
            }
            result.onSuccess {
                _toastMessage.value = "Record updated"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Could not save record"
            }
        }
    }

    fun markActivityPaid(activity: FarmActivity) {
        saveOrUpdateActivity(
            activity.copy(
                amountPaid = activity.totalAmount,
                paymentStatus = "PAID"
            )
        )
    }

    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            repository.deleteActivity(activityId).onSuccess {
                _toastMessage.value = "Record deleted"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to delete record"
            }
        }
    }

    fun saveOrUpdateMemory(memory: LearnedMemory) {
        viewModelScope.launch {
            val result = if (memory.id.isBlank()) {
                repository.addMemory(memory).map { Unit }
            } else {
                repository.updateMemory(memory)
            }
            result.onSuccess {
                _toastMessage.value = "Farm memory saved"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to save memory"
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId).onSuccess {
                _toastMessage.value = "Memory removed"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to delete memory"
            }
        }
    }

    fun deleteCustomRecordType(typeId: String) {
        viewModelScope.launch {
            repository.deleteCustomRecordType(typeId).onSuccess {
                _toastMessage.value = "Custom section removed"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to delete custom section"
            }
        }
    }

    fun updateProfile(ownerName: String, farmName: String, currency: String, goalNote: String) {
        viewModelScope.launch {
            repository.updateProfile(
                ownerName = ownerName,
                farmName = farmName,
                currencySymbol = currency,
                dailyGoalNote = goalNote
            ).onSuccess {
                _toastMessage.value = "Farm profile updated"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun restartOnboarding() {
        viewModelScope.launch {
            repository.updateProfile(onboardingCompleted = false, onboardingStep = 0L)
            val firstName = uiState.value.profile.ownerName.split(" ").firstOrNull().orEmpty().ifBlank { "Friend" }
            repository.addChatMessage(
                ChatMessage(
                    userId = currentUserId,
                    sender = ChatSender.BUDDY.name,
                    text = "Sure thing, $firstName! Let's refresh what I know about ${uiState.value.profile.farmName}. What crops or livestock are you focusing on right now?"
                )
            )
            _quickReplies.value = listOf("Maize & tomatoes", "Poultry & eggs", "Cassava & yam")
        }
    }

    fun showNotice(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun computeAnalytics(activities: List<FarmActivity>): FarmAnalyticsSummary {
        var revenue = 0.0
        var expenses = 0.0
        var unpaid = 0.0
        val harvestUnits = mutableMapOf<String, Double>()
        val cropRevenue = mutableMapOf<String, Double>()
        val debtors = mutableMapOf<String, Double>()
        val monthly = linkedMapOf<String, Pair<Double, Double>>()

        activities.forEach { act ->
            val monthKey = if (act.activityDate.length >= 7) act.activityDate.substring(0, 7) else "Other"
            val prevPair = monthly[monthKey] ?: (0.0 to 0.0)
            when (act.parsedRecordType) {
                RecordType.SALE, RecordType.CONSULTATION -> {
                    revenue += act.totalAmount
                    monthly[monthKey] = (prevPair.first + act.totalAmount) to prevPair.second
                    val cropKey = act.itemOrCrop.ifBlank { act.title }
                    cropRevenue[cropKey] = (cropRevenue[cropKey] ?: 0.0) + act.totalAmount
                    if (act.balanceDue > 0.0 && act.partyName.isNotBlank()) {
                        unpaid += act.balanceDue
                        debtors[act.partyName] = (debtors[act.partyName] ?: 0.0) + act.balanceDue
                    }
                }
                RecordType.EXPENSE, RecordType.LABOR, RecordType.CUSTOM -> {
                    expenses += act.totalAmount
                    monthly[monthKey] = prevPair.first to (prevPair.second + act.totalAmount)
                }
                RecordType.HARVEST -> {
                    val cropKey = act.itemOrCrop.ifBlank { act.title }
                    harvestUnits[cropKey] = (harvestUnits[cropKey] ?: 0.0) + act.quantity
                }
            }
        }

        return FarmAnalyticsSummary(
            totalRevenue = revenue,
            totalExpenses = expenses,
            netProfit = revenue - expenses,
            totalUnpaidReceivables = unpaid,
            totalHarvestUnits = harvestUnits,
            cropRevenueMap = cropRevenue,
            debtorBalances = debtors,
            monthlyNetMap = monthly,
            activityCount = activities.size
        )
    }

    private fun buildDailyGreetingAndTip(
        profile: FarmProfile,
        activities: List<FarmActivity>,
        analytics: FarmAnalyticsSummary
    ): Pair<String, String?> {
        val firstName = profile.ownerName.split(" ").firstOrNull().orEmpty().ifBlank { "Friend" }
        val headline = "${timeOfDayGreeting()}, $firstName!"
        val topDebtor = analytics.debtorBalances.entries.maxByOrNull { it.value }
        val latestHarvest = activities.firstOrNull { it.parsedRecordType == RecordType.HARVEST }

        val tip = when {
            !profile.onboardingCompleted ->
                "Getting to know ${profile.farmName} (${profile.onboardingStep}/7)"
            topDebtor != null ->
                "${topDebtor.key} owes ${formatMoney(topDebtor.value, profile.currencySymbol)}"
            latestHarvest != null ->
                "Recent harvest: ${latestHarvest.itemOrCrop} (${latestHarvest.plotOrUnit.ifBlank { "Farm" }})"
            analytics.totalRevenue > 0 ->
                "Net profit: ${formatMoney(analytics.netProfit, profile.currencySymbol)}"
            else -> null
        }
        return headline to tip
    }

    private fun timeOfDayGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
