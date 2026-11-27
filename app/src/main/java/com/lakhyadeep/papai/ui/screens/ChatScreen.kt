package com.lakhyadeep.papai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lakhyadeep.papai.data.local.db.AssistantEntity
import com.lakhyadeep.papai.data.local.db.ConversationEntity
import com.lakhyadeep.papai.data.local.db.MessageEntity
import com.lakhyadeep.papai.data.local.db.MessageRole
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.ui.components.AssistantAvatar
import com.lakhyadeep.papai.ui.components.DeviceMemoryStrip
import com.lakhyadeep.papai.ui.components.MarkdownMessageView
import com.lakhyadeep.papai.ui.components.MemoryWarningDialog
import com.lakhyadeep.papai.ui.components.ModelStatusPill
import com.lakhyadeep.papai.ui.viewmodel.ChatUiState
import com.lakhyadeep.papai.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showToolsDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var renamingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput { transcript ->
                inputText = if (inputText.isBlank()) transcript else "$inputText $transcript"
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size, uiState.streamingText.length, uiState.preferences.autoScrollChat) {
        if (uiState.preferences.autoScrollChat && (uiState.messages.isNotEmpty() || uiState.streamingText.isNotEmpty())) {
            val targetIdx = (uiState.messages.size + if (uiState.isGenerating) 1 else 0 - 1).coerceAtLeast(0)
            if (targetIdx > 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = uiState.activeAssistant?.name ?: "Papi AI",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ModelStatusPill(
                        loadState = uiState.loadState,
                        onClick = onNavigateToModels,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { showHistorySheet = true },
                    modifier = Modifier.testTag("chat_history_button")
                ) {
                    Icon(Icons.Filled.History, contentDescription = "Conversation history")
                }
                IconButton(
                    onClick = { viewModel.startNewChat() },
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New chat")
                }
                if (uiState.messages.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear conversation")
                    }
                }
            }
        )

        uiState.errorBanner?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        uiState.voiceInput.errorMessage?.let { vErr ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = vErr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearVoiceError() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss voice error", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Main chat body or Home dashboard
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                HomeDashboardSection(
                    uiState = uiState,
                    onSelectAssistant = { viewModel.selectAssistant(it) },
                    onSelectConversation = { viewModel.selectConversation(it.id) },
                    onLoadModel = { viewModel.requestLoadModel(it) },
                    onNavigateToModels = onNavigateToModels,
                    onQuickPrompt = { prompt -> inputText = prompt }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            ttsState = uiState.ttsState,
                            onEditUserMessage = { editingMessage = msg },
                            onRegenerate = { viewModel.regenerateResponse(msg) },
                            onSpeak = { viewModel.speakMessage(msg.id, msg.content) },
                            onPauseTts = { viewModel.pauseTts() },
                            onResumeTts = { viewModel.resumeTts() },
                            onStopTts = { viewModel.stopTts() }
                        )
                    }

                    if (uiState.isGenerating) {
                        item(key = "streaming_bubble") {
                            StreamingAssistantBubble(
                                text = uiState.streamingText,
                                tokenCount = uiState.liveTokenCount,
                                tps = uiState.liveTokensPerSecond,
                                modelName = (uiState.loadState as? ModelLoadState.Loaded)?.modelName ?: "Model"
                            )
                        }
                    }
                }
            }
        }

        // Voice status bar while recording
        if (uiState.voiceInput.isListening) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.voiceInput.partialText.ifBlank { "Listening..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.voiceInput.statusNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    TextButton(onClick = { viewModel.stopVoiceInput() }) {
                        Text("Stop")
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.preferences.enableLocalTools) {
                    IconButton(
                        onClick = { showToolsDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_local_tools_button")
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = "Local offline tools")
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (uiState.loadState is ModelLoadState.Loaded) "Message Papi AI offline..."
                            else "Load a GGUF model to start chatting"
                        )
                    },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                IconButton(
                    onClick = {
                        if (uiState.voiceInput.isListening) {
                            viewModel.stopVoiceInput()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.startVoiceInput { transcript ->
                                    inputText = if (inputText.isBlank()) transcript else "$inputText $transcript"
                                }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = if (uiState.voiceInput.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (uiState.voiceInput.isListening) "Stop voice input" else "Voice input",
                        tint = if (uiState.voiceInput.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.isGenerating) {
                    FilledIconButton(
                        onClick = { viewModel.stopGeneration() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generation")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val payload = inputText
                            inputText = ""
                            viewModel.sendMessage(payload)
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            ConversationHistorySheet(
                conversations = uiState.conversations,
                activeId = uiState.activeConversationId,
                searchQuery = uiState.searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSelect = {
                    viewModel.selectConversation(it.id)
                    showHistorySheet = false
                },
                onRename = { renamingConversation = it },
                onDelete = { viewModel.deleteConversation(it.id) },
                onNewChat = {
                    viewModel.startNewChat()
                    showHistorySheet = false
                }
            )
        }
    }

    editingMessage?.let { msg ->
        var edited by remember(msg.id) { mutableStateOf(msg.content) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )
            },
            confirmButton = {
                Button(onClick = {
                    editingMessage = null
                    viewModel.editUserMessageAndResend(msg, edited)
                }) {
                    Text("Save & Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    renamingConversation?.let { conv ->
        var titleText by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameConversation(conv.id, titleText)
                    renamingConversation = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingConversation = null }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This deletes messages in the current conversation only. Downloaded GGUF models are never deleted.") },
            confirmButton = {
                Button(onClick = {
                    showClearConfirm = false
                    viewModel.clearCurrentConversation()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showToolsDialog) {
        LocalToolsQuickDialog(
            onDismiss = { showToolsDialog = false },
            onRunTool = { name, args ->
                showToolsDialog = false
                viewModel.runDirectLocalTool(name, args)
            }
        )
    }

    uiState.pendingMemoryWarning?.let { warning ->
        MemoryWarningDialog(
            warning = warning,
            onDismiss = { viewModel.dismissMemoryWarning() },
            onLoadAnyway = { viewModel.requestLoadModel(warning.model, forceLoad = true) }
        )
    }
}

@Composable
private fun HomeDashboardSection(
    uiState: ChatUiState,
    onSelectAssistant: (AssistantEntity) -> Unit,
    onSelectConversation: (ConversationEntity) -> Unit,
    onLoadModel: (com.lakhyadeep.papai.data.local.db.ModelEntity) -> Unit,
    onNavigateToModels: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("100% On-Device Private Chat", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = when (val st = uiState.loadState) {
                            is ModelLoadState.Loaded -> "Active model: ${st.modelName} (loaded in ${st.loadTimeMs} ms). Ready offline."
                            is ModelLoadState.Loading -> "Loading ${st.modelName} into memory..."
                            is ModelLoadState.Error -> "Error: ${st.message}"
                            ModelLoadState.Unloaded -> if (uiState.downloadedModels.isEmpty()) {
                                "Download a compact GGUF model (like Qwen2.5 0.5B or Llama 3.2 1B) or import one from your device to begin."
                            } else {
                                "Select one of your local GGUF models below to start chatting offline."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.loadState !is ModelLoadState.Loaded) {
                        if (uiState.downloadedModels.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.downloadedModels) { model ->
                                    Button(onClick = { onLoadModel(model) }) {
                                        Text("Load ${model.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = onNavigateToModels,
                                modifier = Modifier.testTag("home_browse_models_button")
                            ) {
                                Text("Browse & Download Models")
                            }
                        }
                    }
                }
            }
        }

        item {
            DeviceMemoryStrip(
                capabilities = uiState.deviceCapabilities,
                offlineMode = uiState.preferences.offlineMode
            )
        }

        item {
            Text("Quick Assistant Selection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.assistants, key = { it.id }) { pal ->
                    val selected = pal.id == uiState.activeAssistant?.id
                    Surface(
                        onClick = { onSelectAssistant(pal) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = if (selected) 4.dp else 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistantAvatar(pal.avatarKey, pal.colorHex, size = 34.dp)
                            Column {
                                Text(pal.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "Temp ${pal.temperature}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Quick Prompts", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val prompts = listOf(
                    "Summarize key points of...",
                    "Write a Kotlin coroutine example",
                    "Explain quantum entanglement simply",
                    "Draft a polite project update email"
                )
                items(prompts) { p ->
                    AssistChip(onClick = { onQuickPrompt(p) }, label = { Text(p) })
                }
            }
        }

        if (uiState.conversations.isNotEmpty()) {
            item {
                Text("Recent Conversations", style = MaterialTheme.typography.titleSmall)
            }
            items(uiState.conversations.take(5), key = { it.id }) { conv ->
                Surface(
                    onClick = { onSelectConversation(conv) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conv.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Tap to continue offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: MessageEntity,
    ttsState: com.lakhyadeep.papai.domain.voice.TtsState,
    onEditUserMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onSpeak: () -> Unit,
    onPauseTts: () -> Unit,
    onResumeTts: () -> Unit,
    onStopTts: () -> Unit
) {
    val isUser = message.role == MessageRole.USER.name
    val isTool = message.role == MessageRole.TOOL.name
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isTool -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isTool) "Local Tool" else (message.modelName ?: "Papi AI"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.tokensPerSecond > 0f) {
                            Text(
                                text = String.format("%.1f tok/s • %d tok", message.tokensPerSecond, message.tokenCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!message.toolCallsSummary.isNullOrBlank() && !isTool) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Tool used: ${message.toolCallsSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    MarkdownMessageView(
                        markdown = message.content,
                        textColor = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Action strip
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy response", modifier = Modifier.size(16.dp))
            }

            if (isUser) {
                IconButton(onClick = onEditUserMessage, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit message", modifier = Modifier.size(16.dp))
                }
            } else if (!isTool) {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerate response", modifier = Modifier.size(16.dp))
                }

                val isThisSpeaking = ttsState.activeMessageId == message.id && ttsState.isSpeaking
                val isThisPaused = ttsState.activeMessageId == message.id && ttsState.isPaused

                if (isThisSpeaking) {
                    IconButton(onClick = onPauseTts, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause speech", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onStopTts, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop speech", modifier = Modifier.size(16.dp))
                    }
                } else if (isThisPaused) {
                    IconButton(onClick = onResumeTts, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume speech", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onStopTts, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop speech", modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    tokenCount: Int,
    tps: Float,
    modelName: String
) {
    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 560.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "$modelName • Generating",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (tokenCount > 0) {
                    Text(
                        text = String.format("%.1f tok/s • %d tok", tps, tokenCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (text.isBlank()) {
                Text(
                    text = "Processing prompt...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownMessageView(markdown = "$text ▍")
            }
        }
    }
}

@Composable
private fun ConversationHistorySheet(
    conversations: List<ConversationEntity>,
    activeId: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (ConversationEntity) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Conversations", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onNewChat) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Chat")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search conversation history...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (conversations.isEmpty()) {
            Text(
                text = "No conversations yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(320.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isCurrent = conv.id == activeId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conv) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRename(conv) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename conversation", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(conv) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete conversation", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LocalToolsQuickDialog(
    onDismiss: () -> Unit,
    onRunTool: (String, Map<String, String>) -> Unit
) {
    var selectedTool by remember { mutableStateOf("calculator") }
    var inputA by remember { mutableStateOf("") }
    var inputB by remember { mutableStateOf("km") }
    var inputC by remember { mutableStateOf("mi") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safe Offline Local Tools") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tools = listOf(
                        "calculator" to "Calculator",
                        "datetime" to "Date/Time",
                        "unit_converter" to "Units",
                        "document_summarizer" to "Summarize",
                        "text_processor" to "Text Stats"
                    )
                    items(tools) { (key, label) ->
                        AssistChip(
                            onClick = { selectedTool = key },
                            label = { Text(label) }
                        )
                    }
                }

                when (selectedTool) {
                    "calculator" -> OutlinedTextField(
                        value = inputA,
                        onValueChange = { inputA = it },
                        label = { Text("Expression (e.g. (25 * 4) + sqrt(144))") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    "datetime" -> Text("Reads local device date, time, and timezone safely offline.", style = MaterialTheme.typography.bodySmall)
                    "unit_converter" -> {
                        OutlinedTextField(
                            value = inputA,
                            onValueChange = { inputA = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inputB,
                                onValueChange = { inputB = it },
                                label = { Text("From (km/kg/c/gb)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputC,
                                onValueChange = { inputC = it },
                                label = { Text("To (mi/lb/f/mb)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    else -> OutlinedTextField(
                        value = inputA,
                        onValueChange = { inputA = it },
                        label = { Text("Paste text or document excerpt") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val args = when (selectedTool) {
                    "calculator" -> mapOf("expression" to inputA)
                    "datetime" -> mapOf("format" to "full")
                    "unit_converter" -> mapOf("value" to inputA, "from" to inputB, "to" to inputC)
                    "document_summarizer" -> mapOf("text" to inputA, "max_sentences" to "3")
                    else -> mapOf("text" to inputA, "operation" to "stats")
                }
                onRunTool(selectedTool, args)
            }) { Text("Run Offline Tool") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
