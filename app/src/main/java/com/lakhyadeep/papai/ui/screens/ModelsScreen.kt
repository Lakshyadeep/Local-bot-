package com.lakhyadeep.papai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lakhyadeep.papai.data.local.db.DownloadStatus
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.data.remote.HfGgufVariant
import com.lakhyadeep.papai.data.remote.HfModelInfo
import com.lakhyadeep.papai.domain.device.CompatibilityStatus
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.ui.components.DeviceMemoryStrip
import com.lakhyadeep.papai.ui.components.MemoryWarningDialog
import com.lakhyadeep.papai.ui.theme.SuccessGreen
import com.lakhyadeep.papai.ui.theme.WarningAmber
import com.lakhyadeep.papai.ui.viewmodel.ModelsUiState
import com.lakhyadeep.papai.ui.viewmodel.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    uiState: ModelsUiState,
    viewModel: ModelsViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var renamingModel by remember { mutableStateOf<ModelEntity?>(null) }
    var deletingModel by remember { mutableStateOf<ModelEntity?>(null) }

    val safImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importLocalGguf(uri)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Model Manager") },
            actions = {
                TextButton(
                    onClick = { safImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("import_gguf_button")
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import GGUF")
                }
            }
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Models (${uiState.localModels.size})") },
                modifier = Modifier.testTag("tab_my_models")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    if (uiState.hfSearchResults.isEmpty() && !uiState.offlineMode) {
                        viewModel.searchHuggingFace("qwen2.5 gguf")
                    }
                },
                text = { Text("Hugging Face") },
                modifier = Modifier.testTag("tab_huggingface")
            )
        }

        uiState.importProgressText?.let { progressTxt ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(progressTxt, style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        uiState.errorMessage?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        uiState.statusMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissStatus() }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (selectedTab == 0) {
            LocalModelsTab(
                uiState = uiState,
                viewModel = viewModel,
                onImportClick = { safImportLauncher.launch(arrayOf("*/*")) },
                onBrowseHfClick = {
                    selectedTab = 1
                    if (uiState.hfSearchResults.isEmpty() && !uiState.offlineMode) {
                        viewModel.searchHuggingFace("qwen2.5 gguf")
                    }
                },
                onRename = { renamingModel = it },
                onDelete = { deletingModel = it }
            )
        } else {
            HuggingFaceExploreTab(uiState = uiState, viewModel = viewModel)
        }
    }

    uiState.selectedRepoDetails?.let { repo ->
        ModalBottomSheet(onDismissRequest = { viewModel.closeRepoDetails() }) {
            RepoVariantsSheet(
                repo = repo,
                isLoading = uiState.isLoadingRepoDetails,
                viewModel = viewModel,
                onDownloadClick = { variant -> viewModel.requestDownloadVariant(repo, variant) }
            )
        }
    }

    uiState.pendingDownloadConfirm?.let { (repo, variant) ->
        val rec = viewModel.evaluateVariant(variant)
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadConfirm() },
            title = { Text("Confirm Model Download") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repository: ${repo.repoId}", style = MaterialTheme.typography.bodyMedium)
                    Text("File: ${variant.fileName}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Size: ${ModelRecommendationEngine.formatBytes(variant.fileSizeBytes)} • ${variant.quantization}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (rec.warningMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = rec.warningMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndStartDownload(repo, variant) },
                    modifier = Modifier.testTag("confirm_model_download_button")
                ) {
                    Text("Download Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissDownloadConfirm() }) {
                    Text("Cancel")
                }
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

    renamingModel?.let { model ->
        var newName by remember(model.id) { mutableStateOf(model.name) }
        AlertDialog(
            onDismissRequest = { renamingModel = null },
            title = { Text("Rename Model") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameModel(model.id, newName)
                    renamingModel = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingModel = null }) { Text("Cancel") }
            }
        )
    }

    deletingModel?.let { model ->
        AlertDialog(
            onDismissRequest = { deletingModel = null },
            title = { Text("Delete Model?") },
            text = { Text("Remove '${model.name}' and its GGUF file from app storage? Your chat history will stay intact.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteModel(model)
                    deletingModel = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingModel = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LocalModelsTab(
    uiState: ModelsUiState,
    viewModel: ModelsViewModel,
    onImportClick: () -> Unit,
    onBrowseHfClick: () -> Unit,
    onRename: (ModelEntity) -> Unit,
    onDelete: (ModelEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            DeviceMemoryStrip(
                capabilities = uiState.deviceCapabilities,
                offlineMode = uiState.offlineMode
            )
            uiState.deviceCapabilities?.let { caps ->
                Text(
                    text = "Available Storage: ${ModelRecommendationEngine.formatMb(caps.availableStorageMb)} • Context budget: ${uiState.contextLength} tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }
        }

        if (uiState.localModels.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text("No Local Models Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Download verified GGUF models from Hugging Face or import an existing .gguf file from your device storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBrowseHfClick) { Text("Browse Hugging Face") }
                            OutlinedButton(onClick = onImportClick) { Text("Import .gguf") }
                        }
                    }
                }
            }
        } else {
            items(uiState.localModels, key = { it.id }) { model ->
                val activeDl = uiState.activeDownloads[model.id]
                val isLoaded = (uiState.loadState as? ModelLoadState.Loaded)?.modelId == model.id
                val isLoadingThis = (uiState.loadState as? ModelLoadState.Loading)?.modelId == model.id
                val rec = viewModel.evaluateRecommendation(model)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLoaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${model.quantization} • ${ModelRecommendationEngine.formatBytes(model.fileSizeBytes)} • ${model.architecture}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onRename(model) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename model", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDelete(model) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete model", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Compatibility badge
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeColor = when (rec.status) {
                                CompatibilityStatus.OPTIMAL -> SuccessGreen
                                CompatibilityStatus.TIGHT_FIT -> MaterialTheme.colorScheme.primary
                                CompatibilityStatus.MEMORY_WARNING -> WarningAmber
                                else -> MaterialTheme.colorScheme.error
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${rec.badgeText} (~${ModelRecommendationEngine.formatMb(rec.estimatedMemoryMb)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (model.ggufVerified) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Verified GGUF",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Download progress or Load/Unload actions
                        val dlStatus = activeDl?.status?.name ?: model.downloadStatus
                        if (!model.isDownloaded) {
                            val total = (activeDl?.totalBytes ?: model.fileSizeBytes).coerceAtLeast(1L)
                            val done = activeDl?.bytesDownloaded ?: model.downloadedBytes
                            val progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            val speedMb = (activeDl?.speedBytesPerSec ?: 0L) / (1024.0 * 1024.0)

                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text(
                                text = when (dlStatus) {
                                    DownloadStatus.DOWNLOADING.name -> String.format("%.1f%% • %.2f MB/s", progress * 100f, speedMb)
                                    DownloadStatus.PAUSED.name -> String.format("Paused at %.1f%%", progress * 100f)
                                    DownloadStatus.FAILED.name -> "Failed: ${activeDl?.error ?: model.downloadError ?: "Network error"}"
                                    else -> "Ready to resume"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (dlStatus == DownloadStatus.DOWNLOADING.name) {
                                    OutlinedButton(onClick = { viewModel.pauseDownload(model.id) }) {
                                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pause")
                                    }
                                } else {
                                    Button(onClick = { viewModel.resumeOrRetryDownload(model) }) {
                                        Icon(
                                            if (dlStatus == DownloadStatus.FAILED.name) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (dlStatus == DownloadStatus.FAILED.name) "Retry" else "Resume")
                                    }
                                }
                                TextButton(onClick = { viewModel.cancelDownload(model.id) }) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isLoadingThis) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Loading into native llama.cpp...", style = MaterialTheme.typography.labelMedium)
                                } else if (isLoaded) {
                                    OutlinedButton(
                                        onClick = { viewModel.unloadModel() },
                                        modifier = Modifier.testTag("unload_model_button")
                                    ) {
                                        Text("Unload Model")
                                    }
                                    Text("Active", style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
                                } else {
                                    Button(
                                        onClick = { viewModel.requestLoadModel(model) },
                                        modifier = Modifier.testTag("load_model_button")
                                    ) {
                                        Text(if (uiState.loadState is ModelLoadState.Loaded) "Switch Model" else "Load Model")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HuggingFaceExploreTab(
    uiState: ModelsUiState,
    viewModel: ModelsViewModel
) {
    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search GGUF models on Hugging Face...") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("hf_search_input")
            )
            Button(
                onClick = { viewModel.searchHuggingFace() },
                enabled = !uiState.isSearchingHf,
                modifier = Modifier.height(54.dp).testTag("hf_search_button")
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search Hugging Face")
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf("Qwen2.5-0.5B-Instruct-GGUF", "Qwen2.5-1.5B-Instruct-GGUF", "Llama-3.2-1B-Instruct-GGUF", "TinyLlama-1.1B", "Phi-3.5-mini-instruct-GGUF")
            items(presets) { preset ->
                AssistChip(
                    onClick = {
                        viewModel.updateSearchQuery(preset)
                        viewModel.searchHuggingFace(preset)
                    },
                    label = { Text(preset.substringBefore("-GGUF")) }
                )
            }
        }

        if (uiState.isSearchingHf) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Querying Hugging Face Hub...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.hfSearchResults, key = { it.repoId }) { repo ->
                    Card(
                        onClick = { viewModel.openRepoDetails(repo) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = repo.repoId,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (repo.isGated) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Gated repo", modifier = Modifier.size(16.dp), tint = WarningAmber)
                                }
                            }
                            Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${repo.capabilities} • ${repo.downloads} downloads • ${repo.likes} likes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoVariantsSheet(
    repo: HfModelInfo,
    isLoading: Boolean,
    viewModel: ModelsViewModel,
    onDownloadClick: (HfGgufVariant) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(repo.repoId, style = MaterialTheme.typography.titleLarge)
        Text(repo.capabilities, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Fetching GGUF variants and file sizes...", style = MaterialTheme.typography.bodySmall)
            }
        } else if (repo.ggufFiles.isEmpty()) {
            Text("No .gguf files found in this repository.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(340.dp)
            ) {
                items(repo.ggufFiles, key = { it.fileName }) { variant ->
                    val rec = viewModel.evaluateVariant(variant)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(variant.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${variant.quantization} • ${ModelRecommendationEngine.formatBytes(variant.fileSizeBytes)} • ${rec.badgeText}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (rec.status) {
                                        CompatibilityStatus.OPTIMAL -> SuccessGreen
                                        CompatibilityStatus.TIGHT_FIT -> MaterialTheme.colorScheme.primary
                                        CompatibilityStatus.MEMORY_WARNING -> WarningAmber
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onDownloadClick(variant) }) {
                                Text("Get")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
