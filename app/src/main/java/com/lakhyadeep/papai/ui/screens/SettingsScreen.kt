package com.lakhyadeep.papai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lakhyadeep.papai.domain.inference.ChatTemplateType
import com.lakhyadeep.papai.ui.theme.ThemeMode
import com.lakhyadeep.papai.ui.viewmodel.SettingsUiState
import com.lakhyadeep.papai.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val inf = uiState.preferences.inference
    var temperature by remember(inf) { mutableFloatStateOf(inf.temperature) }
    var topP by remember(inf) { mutableFloatStateOf(inf.topP) }
    var topK by remember(inf) { mutableIntStateOf(inf.topK) }
    var repeatPenalty by remember(inf) { mutableFloatStateOf(inf.repeatPenalty) }
    var maxTokens by remember(inf) { mutableIntStateOf(inf.maxTokens) }
    var contextLength by remember(inf) { mutableIntStateOf(inf.contextLength) }
    var systemPrompt by remember(inf) { mutableStateOf(inf.systemPrompt) }
    var chatTemplate by remember(inf) { mutableStateOf(inf.chatTemplate) }
    var stopSequences by remember(inf) { mutableStateOf(inf.stopSequences) }
    var hfTokenInput by remember(uiState.preferences.huggingFaceToken) {
        mutableStateOf(uiState.preferences.huggingFaceToken)
    }

    var templateDropdownOpen by remember { mutableStateOf(false) }
    var ttsVoiceDropdownOpen by remember { mutableStateOf(false) }
    var ttsSpeed by remember(uiState.preferences.ttsSpeed) { mutableFloatStateOf(uiState.preferences.ttsSpeed) }

    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            kotlinx.coroutines.delay(2500L)
            viewModel.clearStatus()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Inference & App Settings") },
            actions = {
                TextButton(onClick = { viewModel.resetDefaults() }) {
                    Text("Reset")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.validationErrors.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            uiState.validationErrors.forEach { err ->
                                Text("• $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            uiState.statusMessage?.let { status ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // Inference Sampling & Context
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sampling & Context Parameters", style = MaterialTheme.typography.titleMedium)

                        Text("Temperature: ${String.format("%.2f", temperature)}", style = MaterialTheme.typography.labelLarge)
                        Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0.0f..2.0f)

                        Text("Top P: ${String.format("%.2f", topP)}", style = MaterialTheme.typography.labelLarge)
                        Slider(value = topP, onValueChange = { topP = it }, valueRange = 0.1f..1.0f)

                        Text("Top K: $topK", style = MaterialTheme.typography.labelLarge)
                        Slider(value = topK.toFloat(), onValueChange = { topK = it.toInt() }, valueRange = 1f..120f)

                        Text("Repeat Penalty: ${String.format("%.2f", repeatPenalty)}", style = MaterialTheme.typography.labelLarge)
                        Slider(value = repeatPenalty, onValueChange = { repeatPenalty = it }, valueRange = 0.9f..1.6f)

                        Text("Max Generated Tokens: $maxTokens", style = MaterialTheme.typography.labelLarge)
                        Slider(value = maxTokens.toFloat(), onValueChange = { maxTokens = (it.toInt() / 64) * 64 }, valueRange = 64f..4096f)

                        Text("Context Window Budget: $contextLength tokens", style = MaterialTheme.typography.labelLarge)
                        Slider(value = contextLength.toFloat(), onValueChange = { contextLength = (it.toInt() / 512) * 512 }, valueRange = 512f..16384f)

                        ExposedDropdownMenuBox(
                            expanded = templateDropdownOpen,
                            onExpandedChange = { templateDropdownOpen = !templateDropdownOpen }
                        ) {
                            OutlinedTextField(
                                value = chatTemplate.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chat Template") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = templateDropdownOpen,
                                onDismissRequest = { templateDropdownOpen = false }
                            ) {
                                ChatTemplateType.entries.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.displayName) },
                                        onClick = {
                                            chatTemplate = t
                                            templateDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("Default System Prompt") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = stopSequences,
                            onValueChange = { stopSequences = it },
                            label = { Text("Stop Sequences (comma-separated)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                viewModel.saveInferenceSettings(
                                    inf.copy(
                                        temperature = temperature,
                                        topP = topP,
                                        topK = topK,
                                        repeatPenalty = repeatPenalty,
                                        maxTokens = maxTokens,
                                        contextLength = contextLength,
                                        systemPrompt = systemPrompt,
                                        chatTemplate = chatTemplate,
                                        stopSequences = stopSequences
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_inference_settings_button")
                        ) {
                            Text("Save Inference Settings")
                        }
                    }
                }
            }

            // Unsupported Native Engine Controls (Explicitly Disabled)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hardware & Native Engine Controls", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Engine: ${uiState.engineCapabilities?.engineName ?: "llama.cpp"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = "0 (Disabled — CPU-only native build)",
                            onValueChange = {},
                            enabled = false,
                            label = { Text("GPU Layer Offload") },
                            supportingText = {
                                Text(uiState.engineCapabilities?.gpuUnavailableReason ?: "GPU backend unavailable on this build.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = "Auto (${uiState.deviceCapabilities?.cpuCores ?: 4} OpenMP threads)",
                            onValueChange = {},
                            enabled = false,
                            label = { Text("CPU Thread Count") },
                            supportingText = {
                                Text("Managed automatically by the native OpenMP runtime in this llama.cpp JNI build.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = "Random (Seed control unsupported)",
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Sampler RNG Seed") },
                            supportingText = {
                                Text("Deterministic seed override is not exposed by this native sampler bridge.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Voice & TTS Settings
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Voice & Text-to-Speech", style = MaterialTheme.typography.titleMedium)

                        ExposedDropdownMenuBox(
                            expanded = ttsVoiceDropdownOpen,
                            onExpandedChange = { ttsVoiceDropdownOpen = !ttsVoiceDropdownOpen }
                        ) {
                            val selectedLabel = uiState.availableTtsVoices
                                .firstOrNull { it.name == uiState.preferences.preferredTtsVoice }?.displayName
                                ?: "System Default Voice"
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("TTS Voice") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ttsVoiceDropdownOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = ttsVoiceDropdownOpen,
                                onDismissRequest = { ttsVoiceDropdownOpen = false }
                            ) {
                                uiState.availableTtsVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.displayName) },
                                        onClick = {
                                            viewModel.setTtsSettings(voice.name, ttsSpeed)
                                            ttsVoiceDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Text("Speech Speed: ${String.format("%.2fx", ttsSpeed)}", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = ttsSpeed,
                            onValueChange = { ttsSpeed = it },
                            onValueChangeFinished = {
                                viewModel.setTtsSettings(uiState.preferences.preferredTtsVoice, ttsSpeed)
                            },
                            valueRange = 0.5f..2.0f
                        )
                    }
                }
            }

            // Privacy, Appearance & Hugging Face Token
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Privacy, Theme & Downloads", style = MaterialTheme.typography.titleMedium)

                        ToggleRow(
                            title = "Strict Offline Mode",
                            subtitle = "Block all Hugging Face network requests; local GGUF inference & SAF imports keep working.",
                            checked = uiState.preferences.offlineMode,
                            onCheckedChange = { viewModel.setOfflineMode(it) },
                            tag = "offline_mode_switch"
                        )

                        ToggleRow(
                            title = "Safe Local AI Tools",
                            subtitle = "Enable offline calculator, date/time, unit converter, and text summarizer.",
                            checked = uiState.preferences.enableLocalTools,
                            onCheckedChange = { viewModel.setEnableLocalTools(it) },
                            tag = "local_tools_switch"
                        )

                        ToggleRow(
                            title = "Auto-scroll Chat",
                            subtitle = "Follow generated tokens automatically while streaming.",
                            checked = uiState.preferences.autoScrollChat,
                            onCheckedChange = { viewModel.setAutoScrollChat(it) },
                            tag = "autoscroll_switch"
                        )

                        Text("Appearance", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = uiState.preferences.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size)
                                ) {
                                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                        }

                        OutlinedTextField(
                            value = hfTokenInput,
                            onValueChange = { hfTokenInput = it },
                            label = { Text("Hugging Face Access Token (Optional)") },
                            supportingText = { Text("Stored locally on device only for gated GGUF downloads.") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.setHuggingFaceToken(hfTokenInput) }) {
                                Text("Save Token")
                            }
                            if (uiState.preferences.huggingFaceToken.isNotEmpty()) {
                                TextButton(onClick = {
                                    hfTokenInput = ""
                                    viewModel.setHuggingFaceToken("")
                                }) { Text("Clear") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
