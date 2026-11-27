package com.lakhyadeep.papai.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakhyadeep.papai.data.local.db.AssistantEntity
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.ui.components.AssistantAvatar
import com.lakhyadeep.papai.ui.viewmodel.AssistantsUiState
import com.lakhyadeep.papai.ui.viewmodel.AssistantsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsScreen(
    uiState: AssistantsUiState,
    viewModel: AssistantsViewModel,
    onStartChatWithAssistant: (AssistantEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingAssistant by remember { mutableStateOf<AssistantEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingAssistant by remember { mutableStateOf<AssistantEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Pals • AI Assistants") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_assistant_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create AI Assistant")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.assistants, key = { it.id }) { pal ->
                val isActive = pal.id == uiState.activeAssistantId
                val convCount = uiState.conversationCountsByAssistant[pal.id] ?: 0
                val preferredModelName = uiState.downloadedModels
                    .firstOrNull { it.id == pal.preferredModelId }?.name

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AssistantAvatar(pal.avatarKey, pal.colorHex, size = 46.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(pal.name, style = MaterialTheme.typography.titleMedium)
                                    if (pal.isPredefined) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                "Preset",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    pal.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "Temp ${String.format("%.2f", pal.temperature)} • Ctx ${pal.contextLength} • $convCount chats" +
                                (preferredModelName?.let { " • Model: $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setActiveAssistant(pal.id)
                                    onStartChatWithAssistant(pal)
                                },
                                modifier = Modifier.testTag("chat_with_pal_${pal.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isActive) "Open Chat" else "Use Pal")
                            }

                            Row {
                                IconButton(onClick = { editingAssistant = pal }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit assistant")
                                }
                                IconButton(onClick = { viewModel.duplicateAssistant(pal) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate assistant")
                                }
                                if (!pal.isPredefined || uiState.assistants.size > 1) {
                                    IconButton(onClick = { deletingAssistant = pal }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete assistant")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingAssistant != null) {
        AssistantEditorDialog(
            initial = editingAssistant,
            downloadedModels = uiState.downloadedModels,
            onDismiss = {
                showCreateDialog = false
                editingAssistant = null
            },
            onSave = { name, desc, avatar, color, prompt, modelId, temp, ctx, maxTok ->
                viewModel.saveAssistant(
                    existingId = editingAssistant?.id,
                    name = name,
                    description = desc,
                    avatarKey = avatar,
                    colorHex = color,
                    systemPrompt = prompt,
                    preferredModelId = modelId,
                    temperature = temp,
                    contextLength = ctx,
                    maxTokens = maxTok
                )
                showCreateDialog = false
                editingAssistant = null
            }
        )
    }

    deletingAssistant?.let { pal ->
        AlertDialog(
            onDismissRequest = { deletingAssistant = null },
            title = { Text("Delete '${pal.name}'?") },
            text = { Text("This removes the assistant configuration. Existing conversations remain accessible.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAssistant(pal)
                    deletingAssistant = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingAssistant = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantEditorDialog(
    initial: AssistantEntity?,
    downloadedModels: List<ModelEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, String, String?, Float, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "You are a helpful private AI assistant.") }
    var avatarKey by remember { mutableStateOf(initial?.avatarKey ?: "spark") }
    var colorHex by remember { mutableLongStateOf(initial?.colorHex ?: 0xFF0D9488) }
    var preferredModelId by remember { mutableStateOf(initial?.preferredModelId) }
    var temperature by remember { mutableFloatStateOf(initial?.temperature ?: 0.7f) }
    var contextLength by remember { mutableIntStateOf(initial?.contextLength ?: 4096) }
    var maxTokens by remember { mutableIntStateOf(initial?.maxTokens ?: 512) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val avatars = listOf("spark", "code", "edit", "school", "palette", "bot")
    val colors = listOf(0xFF0D9488, 0xFF2563EB, 0xFFD97706, 0xFF059669, 0xFFDB2777, 0xFF7C3AED)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create AI Pal" else "Edit ${initial.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("assistant_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Avatar & Accent Color", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(avatars) { key ->
                        Surface(
                            onClick = { avatarKey = key },
                            shape = CircleShape,
                            tonalElevation = if (avatarKey == key) 6.dp else 0.dp,
                            color = if (avatarKey == key) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            AssistantAvatar(key, colorHex, size = 40.dp, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { colorHex = hex }
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                ) {
                    val currentModelLabel = downloadedModels.firstOrNull { it.id == preferredModelId }?.name ?: "Any Loaded Model"
                    OutlinedTextField(
                        value = currentModelLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preferred Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any Loaded Model") },
                            onClick = {
                                preferredModelId = null
                                modelDropdownExpanded = false
                            }
                        )
                        downloadedModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    preferredModelId = model.id
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Temperature: ${String.format("%.2f", temperature)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1.5f
                )

                Text("Context Length: $contextLength tokens", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = contextLength.toFloat(),
                    onValueChange = { contextLength = (it.toInt() / 512) * 512 },
                    valueRange = 512f..8192f
                )

                Text("Max Response Tokens: $maxTokens", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = (it.toInt() / 64) * 64 },
                    valueRange = 128f..2048f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, description, avatarKey, colorHex, systemPrompt, preferredModelId, temperature, contextLength, maxTokens)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_assistant_button")
            ) {
                Text("Save Pal")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
