package com.lakhyadeep.papai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lakhyadeep.papai.R
import com.lakhyadeep.papai.domain.device.CompatibilityStatus
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.ui.theme.SuccessGreen
import com.lakhyadeep.papai.ui.theme.WarningAmber
import com.lakhyadeep.papai.ui.viewmodel.PendingMemoryWarning

@Composable
fun PapiSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.84f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, tween(350))
        kotlinx.coroutines.delay(550L)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(320))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0B111E), Color(0xFF131C2D))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.papi_ai_icon_foreground),
                    contentDescription = "Papi AI logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Papi AI",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color(0xFF2DD4BF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Private On-Device Intelligence",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantAvatar(
    avatarKey: String,
    colorHex: Long,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (avatarKey) {
        "code" -> Icons.Filled.Code
        "edit" -> Icons.Filled.Edit
        "school" -> Icons.Filled.School
        "palette" -> Icons.Filled.Brush
        "spark" -> Icons.Filled.AutoAwesome
        else -> Icons.Filled.SmartToy
    }
    val tintColor = Color(colorHex)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
fun ModelStatusPill(
    loadState: ModelLoadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, dotColor, showSpinner) = when (loadState) {
        is ModelLoadState.Unloaded -> Triple("No model loaded", MaterialTheme.colorScheme.outline, false)
        is ModelLoadState.Loading -> Triple("Loading ${loadState.modelName}...", WarningAmber, true)
        is ModelLoadState.Loaded -> Triple(loadState.modelName, SuccessGreen, false)
        is ModelLoadState.Error -> Triple("Load failed", MaterialTheme.colorScheme.error, false)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.testTag("model_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = WarningAmber
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DeviceMemoryStrip(
    capabilities: DeviceCapabilities?,
    offlineMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (capabilities == null) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = "Device memory",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "RAM: ${ModelRecommendationEngine.formatMb(capabilities.availableRamMb)} free / ${ModelRecommendationEngine.formatMb(capabilities.totalRamMb)}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (offlineMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (offlineMode) "OFFLINE" else capabilities.primaryAbi,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (offlineMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun MemoryWarningDialog(
    warning: PendingMemoryWarning,
    onDismiss: () -> Unit,
    onLoadAnyway: () -> Unit
) {
    val isCritical = warning.recommendation.status == CompatibilityStatus.INSUFFICIENT_RAM ||
        warning.recommendation.status == CompatibilityStatus.ARCH_UNSUPPORTED

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isCritical) MaterialTheme.colorScheme.error else WarningAmber
            )
        },
        title = { Text(warning.recommendation.badgeText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = warning.recommendation.warningMessage
                        ?: "This model may exceed available device RAM.",
                    style = MaterialTheme.typography.bodyMedium
                )
                warning.recommendation.suggestedAction?.let { hint ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Tip: $hint",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (warning.recommendation.status != CompatibilityStatus.ARCH_UNSUPPORTED) {
                Button(
                    onClick = onLoadAnyway,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("confirm_load_despite_warning")
                ) {
                    Text("Load Anyway")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
