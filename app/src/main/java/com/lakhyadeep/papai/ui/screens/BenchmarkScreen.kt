package com.lakhyadeep.papai.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lakhyadeep.papai.data.local.db.BenchmarkEntity
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.ui.viewmodel.BenchmarkUiState
import com.lakhyadeep.papai.ui.viewmodel.BenchmarkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    uiState: BenchmarkUiState,
    viewModel: BenchmarkViewModel,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedModel by remember { mutableStateOf<ModelEntity?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.downloadedModels, uiState.loadState) {
        val loadedId = (uiState.loadState as? ModelLoadState.Loaded)?.modelId
        if (selectedModel == null || uiState.downloadedModels.none { it.id == selectedModel?.id }) {
            selectedModel = uiState.downloadedModels.firstOrNull { it.id == loadedId }
                ?: uiState.downloadedModels.firstOrNull()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Performance Benchmark") },
            actions = {
                if (uiState.benchmarks.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearBenchmarks() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear benchmark history")
                    }
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Device Hardware Telemetry
            item {
                uiState.deviceCapabilities?.let { caps ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Device Hardware", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${caps.manufacturer} ${caps.deviceModel} • ${caps.primaryAbi} • ${caps.cpuCores} CPU cores",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "RAM: ${ModelRecommendationEngine.formatMb(caps.availableRamMb)} free / ${ModelRecommendationEngine.formatMb(caps.totalRamMb)} • ${caps.gpuBackendInfo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Benchmark Runner Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Run Real Inference Benchmark", style = MaterialTheme.typography.titleMedium)

                        if (uiState.downloadedModels.isEmpty()) {
                            Text(
                                "Download or import a local GGUF model first to measure real loading, prompt eval, and token generation speeds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onNavigateToModels) {
                                Text("Go to Model Manager")
                            }
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { if (!uiState.isRunning) dropdownExpanded = !dropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedModel?.name ?: "Select GGUF model",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Target Model") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    uiState.downloadedModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text("${model.name} (${model.quantization})") },
                                            onClick = {
                                                selectedModel = model
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (uiState.isRunning) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = uiState.progressLabel.ifBlank { "Benchmarking..." },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            uiState.errorMessage?.let { err ->
                                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }

                            Button(
                                onClick = { selectedModel?.let { viewModel.runBenchmark(it) } },
                                enabled = !uiState.isRunning && selectedModel != null,
                                modifier = Modifier.fillMaxWidth().testTag("run_benchmark_button")
                            ) {
                                Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (uiState.isRunning) "Measuring..." else "Start Benchmark")
                            }
                        }
                    }
                }
            }

            // Speed Comparison Chart
            if (uiState.benchmarks.isNotEmpty()) {
                item {
                    BenchmarkComparisonChart(benchmarks = uiState.benchmarks.take(6))
                }
                item {
                    Text("Recorded Measurements", style = MaterialTheme.typography.titleSmall)
                }
                items(uiState.benchmarks, key = { it.id }) { item ->
                    BenchmarkResultCard(
                        item = item,
                        onDelete = { viewModel.deleteBenchmark(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkComparisonChart(benchmarks: List<BenchmarkEntity>) {
    val maxGenTps = benchmarks.maxOfOrNull { it.tokenGenSpeedTps }?.coerceAtLeast(1f) ?: 1f
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Model Generation Speed Comparison (tok/s)", style = MaterialTheme.typography.titleSmall)
            benchmarks.forEach { b ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${b.modelName} (${b.quantization})",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f tok/s", b.tokenGenSpeedTps),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                        val trackRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        drawRoundRect(
                            color = secondaryColor.copy(alpha = 0.14f),
                            size = size,
                            cornerRadius = trackRadius
                        )
                        val fraction = (b.tokenGenSpeedTps / maxGenTps).coerceIn(0.03f, 1f)
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset.Zero,
                            size = Size(width = size.width * fraction, height = size.height),
                            cornerRadius = trackRadius
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(
    item: BenchmarkEntity,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.modelName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${item.quantization} • ${item.deviceModel} (${item.cpuAbi})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete benchmark", modifier = Modifier.size(16.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricStat("Load", "${item.loadTimeMs} ms")
                MetricStat("Prompt", String.format("%.1f t/s", item.promptEvalSpeedTps))
                MetricStat("Gen", String.format("%.1f t/s", item.tokenGenSpeedTps))
                MetricStat("RAM", ModelRecommendationEngine.formatMb(item.estimatedRamMb))
            }
            Text(
                text = "Generated ${item.tokensGenerated} tokens in ${item.totalGenTimeMs} ms • Context ${item.contextLength}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
