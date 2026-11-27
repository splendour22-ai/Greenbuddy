package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ChatMessage
import com.example.data.ChatSender
import com.example.data.FarmActivity
import com.example.data.ProposalStatus
import com.example.data.ProposedActivityDraft
import com.example.data.ProposedCustomTypeDraft
import com.example.data.ProposedMemoryDraft
import com.example.data.formatMoney
import com.example.data.formatQuantity
import com.example.ui.BuddyScreenState
import com.example.ui.SessionCardAttachments
import com.example.ui.components.ActivityEditorSheet
import com.example.ui.components.FarmBarChartCard
import com.example.ui.voice.VoiceCaptureState
import com.example.ui.voice.VoiceInputController

@Composable
fun BuddyChatScreen(
    state: BuddyScreenState,
    onSendMessage: (String) -> Unit,
    onSendVoiceAudio: (ByteArray, String) -> Unit,
    onConfirmActivity: (String, FarmActivity) -> Unit,
    onDismissActivity: (String) -> Unit,
    onConfirmMemory: (String) -> Unit,
    onDismissMemory: (String) -> Unit,
    onApproveCustomType: (String) -> Unit,
    onDismissCustomType: (String) -> Unit,
    onEditSavedActivity: (FarmActivity) -> Unit,
    onDeleteSavedActivity: (String) -> Unit,
    onNavigateToInsights: () -> Unit,
    onShowNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf<VoiceCaptureState>(VoiceCaptureState.Idle) }
    var editingProposalPair by remember { mutableStateOf<Pair<String, FarmActivity>?>(null) }
    var editingSavedActivity by remember { mutableStateOf<FarmActivity?>(null) }

    val listState = rememberLazyListState()

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onStateChanged = { newState ->
                voiceState = newState
                if (newState is VoiceCaptureState.ListeningSpeech && newState.partialText.isNotBlank()) {
                    inputText = newState.partialText
                }
            },
            onTranscriptFinal = { transcript ->
                inputText = ""
                onSendMessage(transcript)
            },
            onAudioClipReady = { bytes, mime ->
                onSendVoiceAudio(bytes, mime)
            },
            onError = { err -> onShowNotice(err) }
        )
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening()
        } else {
            onShowNotice("Microphone permission is needed for voice input. You can still type below.")
        }
    }

    LaunchedEffect(state.chatMessages.size, state.isAiThinking) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Warm Greeting Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Eco,
                                contentDescription = "GreenHarvest Buddy",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = state.dailyCheckInHeadline.ifBlank { "GreenHarvest Buddy" },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.profile.farmName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.dailyPulseTip?.let { tip ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { onNavigateToInsights() }
                        ) {
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_message_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.chatMessages, key = { it.id.ifBlank { it.text.hashCode() } }) { message ->
                val attachment = state.attachmentsByMessageId[message.id]
                val linkedAct = message.linkedActivityId?.let { id ->
                    state.activities.firstOrNull { it.id == id }
                }
                ChatMessageRow(
                    message = message,
                    attachment = attachment,
                    linkedActivity = linkedAct,
                    currencySymbol = state.profile.currencySymbol,
                    onConfirmActivity = { act -> onConfirmActivity(message.id, act) },
                    onEditProposal = { act -> editingProposalPair = message.id to act },
                    onDismissActivity = { onDismissActivity(message.id) },
                    onConfirmMemory = { onConfirmMemory(message.id) },
                    onDismissMemory = { onDismissMemory(message.id) },
                    onApproveCustomType = { onApproveCustomType(message.id) },
                    onDismissCustomType = { onDismissCustomType(message.id) },
                    onEditSavedActivity = { act -> editingSavedActivity = act }
                )
            }

            if (state.isAiThinking) {
                item(key = "buddy_thinking") {
                    BuddyThinkingBubble()
                }
            }
        }

        // Quick Suggestion Chips
        AnimatedVisibility(visible = state.quickReplies.isNotEmpty() && !state.isAiThinking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.quickReplies.forEach { reply ->
                    AssistChip(
                        onClick = { onSendMessage(reply) },
                        label = { Text(reply, style = MaterialTheme.typography.labelMedium) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }

        // Active Voice Listening Indicator
        AnimatedVisibility(visible = voiceState !is VoiceCaptureState.Idle) {
            VoiceActiveBanner(
                voiceState = voiceState,
                onStop = { voiceController.stopAndFinish() }
            )
        }

        // Composer Input Area (Text + Voice)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.composer_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !state.isAiThinking) {
                                val toSend = inputText
                                inputText = ""
                                onSendMessage(toSend)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("composer_input")
                )

                val isListening = voiceState !is VoiceCaptureState.Idle
                FilledIconButton(
                    onClick = {
                        if (isListening) {
                            voiceController.stopAndFinish()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                voiceController.startListening()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isListening) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isListening) "Stop voice input" else "Start voice input"
                    )
                }

                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !state.isAiThinking) {
                            val toSend = inputText
                            inputText = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isAiThinking,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }

    editingProposalPair?.let { (msgId, draftAct) ->
        ActivityEditorSheet(
            initialActivity = draftAct,
            currencySymbol = state.profile.currencySymbol,
            isExistingRecord = false,
            confirmButtonLabel = stringResource(R.string.confirm_and_save),
            onSave = { updated ->
                onConfirmActivity(msgId, updated)
                editingProposalPair = null
            },
            onDismiss = { editingProposalPair = null }
        )
    }

    editingSavedActivity?.let { saved ->
        ActivityEditorSheet(
            initialActivity = saved,
            currencySymbol = state.profile.currencySymbol,
            isExistingRecord = true,
            confirmButtonLabel = "Save Changes",
            onSave = { updated ->
                onEditSavedActivity(updated)
                editingSavedActivity = null
            },
            onDelete = {
                onDeleteSavedActivity(saved.id)
                editingSavedActivity = null
            },
            onDismiss = { editingSavedActivity = null }
        )
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    attachment: SessionCardAttachments?,
    linkedActivity: FarmActivity?,
    currencySymbol: String,
    onConfirmActivity: (FarmActivity) -> Unit,
    onEditProposal: (FarmActivity) -> Unit,
    onDismissActivity: () -> Unit,
    onConfirmMemory: () -> Unit,
    onDismissMemory: () -> Unit,
    onApproveCustomType: () -> Unit,
    onDismissCustomType: () -> Unit,
    onEditSavedActivity: (FarmActivity) -> Unit
) {
    val isUser = message.parsedSender == ChatSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Eco,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                tonalElevation = if (isUser) 0.dp else 1.dp,
                border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Persisted Saved Activity Pill (if saved earlier or in this session)
        if (!isUser && linkedActivity != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(start = 36.dp)
                    .widthIn(max = 310.dp)
                    .clickable { onEditSavedActivity(linkedActivity) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message.cardSummary ?: "${linkedActivity.parsedRecordType.label}: ${linkedActivity.title}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit saved record",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active Session Interactive Confirmation Cards
        attachment?.activityDraft?.let { draft ->
            if (draft.status == ProposalStatus.PENDING) {
                ActivityConfirmationCard(
                    draft = draft,
                    currencySymbol = currencySymbol,
                    onConfirm = { onConfirmActivity(draft.activity) },
                    onEdit = { onEditProposal(draft.activity) },
                    onDismiss = onDismissActivity,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }
        }

        attachment?.customTypeDraft?.let { ctDraft ->
            CustomTypeProposalCard(
                draft = ctDraft,
                onApprove = onApproveCustomType,
                onDismiss = onDismissCustomType,
                modifier = Modifier.padding(start = 36.dp)
            )
        }

        attachment?.memoryDraft?.let { memDraft ->
            MemoryProposalCard(
                draft = memDraft,
                currencySymbol = currencySymbol,
                onConfirm = onConfirmMemory,
                onDismiss = onDismissMemory,
                modifier = Modifier.padding(start = 36.dp)
            )
        }

        attachment?.chartData?.let { chart ->
            FarmBarChartCard(
                chartData = chart,
                currencySymbol = currencySymbol,
                modifier = Modifier
                    .padding(start = 36.dp)
                    .widthIn(max = 330.dp)
            )
        }
    }
}

@Composable
private fun ActivityConfirmationCard(
    draft: ProposedActivityDraft,
    currencySymbol: String,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val act = draft.activity
    Card(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = act.customTypeName ?: act.parsedRecordType.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = act.activityDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = act.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (act.quantity > 0.0) {
                    DetailLine("Quantity", formatQuantity(act.quantity, act.unit))
                }
                if (act.totalAmount > 0.0) {
                    DetailLine("Total", formatMoney(act.totalAmount, currencySymbol))
                    DetailLine(
                        "Paid",
                        "${formatMoney(act.amountPaid, currencySymbol)} (${act.parsedPaymentStatus.label})"
                    )
                }
                if (act.partyName.isNotBlank()) {
                    DetailLine(act.parsedPartyRole.label, act.partyName)
                }
                if (act.plotOrUnit.isNotBlank()) {
                    DetailLine("Location", act.plotOrUnit)
                }
                if (act.notes.isNotBlank()) {
                    DetailLine("Notes", act.notes)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("confirm_activity_card_button")
                ) {
                    Text(stringResource(R.string.confirm_and_save), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("edit_activity_card_button")
                ) {
                    Text(stringResource(R.string.edit_details), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dismiss), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MemoryProposalCard(
    draft: ProposedMemoryDraft,
    currencySymbol: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (draft.status == ProposalStatus.DISMISSED) return

    Card(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (draft.markOnboardingComplete) "Farm Setup Summary" else "Remember for Next Time?",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            draft.memories.forEach { mem ->
                val priceNote = mem.typicalPrice?.let { " • ${formatMoney(it, currencySymbol)}" } ?: ""
                Text(
                    text = "• ${mem.label} (${mem.parsedCategory.displayTitle}): ${mem.detail}$priceNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (draft.status == ProposalStatus.CONFIRMED) {
                Text(
                    text = "Saved to Farm Memory ✓",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("save_memory_card_button")
                    ) {
                        Text(stringResource(R.string.save_to_memory), style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTypeProposalCard(
    draft: ProposedCustomTypeDraft,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (draft.status == ProposalStatus.DISMISSED) return

    Card(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "New Record Section: ${draft.customType.name}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            if (draft.customType.description.isNotBlank()) {
                Text(
                    text = draft.customType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (draft.customType.fieldNames.isNotEmpty()) {
                Text(
                    text = "Fields: ${draft.customType.fieldNames.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (draft.status == ProposalStatus.CONFIRMED) {
                Text(
                    text = "Section created & logged ✓",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("approve_custom_type_button")
                    ) {
                        Text(stringResource(R.string.approve_create_section), style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.not_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BuddyThinkingBubble() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Buddy is thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceActiveBanner(
    voiceState: VoiceCaptureState,
    onStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStop() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.scale(pulse)
                )
                Text(
                    text = when (voiceState) {
                        is VoiceCaptureState.ListeningSpeech ->
                            if (voiceState.partialText.isNotBlank()) voiceState.partialText
                            else stringResource(R.string.voice_listening)
                        VoiceCaptureState.RecordingAudioFallback ->
                            stringResource(R.string.voice_recording)
                        VoiceCaptureState.Idle -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "Tap to send",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
