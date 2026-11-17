package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AvatarConfig
import com.example.model.Mascot
import com.example.model.NudgeType
import com.example.model.Squad
import com.example.ui.theme.TangerinePrimary
import com.example.ui.theme.VoltLime
import java.text.NumberFormat
import java.util.Locale

fun formatStepCount(steps: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(steps)

fun launchSquadShareSheet(context: Context, squad: Squad) {
    val shareText = "Join my WaddleUp squad \"${squad.name}\"!\n" +
        "Use invite code: ${squad.inviteCode}\n" +
        "\"${squad.motto.ifBlank { "Time to prove you actually leave the couch!" }}\""
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join my WaddleUp Squad: ${squad.name}")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Invite friends to ${squad.name}"))
}

@Composable
fun MascotBadge(
    mascot: Mascot,
    size: Dp = 48.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(mascot.badgeColorHex))
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mascot.emoji,
            fontSize = (size.value * 0.54f).sp
        )
    }
}

@Composable
fun WaddleStepRing(
    steps: Long,
    goal: Long,
    rankEmoji: String,
    rankTitle: String,
    modifier: Modifier = Modifier
) {
    val safeGoal = goal.coerceAtLeast(1000L)
    val rawProgress = (steps.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val animatedSweep by animateFloatAsState(
        targetValue = rawProgress * 280f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "step_ring_sweep"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = VoltLime

    Box(
        modifier = modifier.size(228.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 18.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, accentColor, TangerinePrimary)
                    ),
                    startAngle = 130f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(text = rankEmoji, fontSize = 30.sp)
            Text(
                text = formatStepCount(steps),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of ${formatStepCount(safeGoal)} goal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = rankTitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun WeeklyBarsChart(
    days: List<Pair<String, Long>>,
    goal: Long,
    modifier: Modifier = Modifier
) {
    val maxSteps = maxOf(goal, days.maxOfOrNull { it.second } ?: 1000L, 1000L)
    val barColor = MaterialTheme.colorScheme.primary
    val goalReachedColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            if (days.isEmpty()) return@Canvas
            val slotWidth = size.width / days.size
            val barWidth = (slotWidth * 0.46f).coerceAtMost(24.dp.toPx())
            days.forEachIndexed { index, (_, steps) ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
                val fraction = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f)
                val barHeight = (size.height * fraction).coerceAtLeast(if (steps > 0) 8.dp.toPx() else 0f)
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = if (steps >= goal) goalReachedColor else barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            days.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendNudgeBottomSheet(
    recipientName: String,
    recipientMascot: Mascot,
    recipientAvatar: AvatarConfig = AvatarConfig.decode(null, recipientMascot),
    onDismiss: () -> Unit,
    onSend: (NudgeType, String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(NudgeType.QUACK_ATTACK) }
    var customNote by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CartoonAvatar(config = recipientAvatar, size = 48.dp, animated = true)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Nudge $recipientName",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Pick a certified friendly roast or cheer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            NudgeType.entries.forEach { type ->
                val isSelected = type == selectedType
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedType = type }
                        .testTag("nudge_option_${type.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = type.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = type.defaultMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = customNote,
                onValueChange = { if (it.length <= 120) customNote = it },
                label = { Text("Optional custom roast (max 120 chars)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_nudge_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSend(selectedType, customNote.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.testTag("send_nudge_confirm_button")
                ) {
                    Text("Launch ${selectedType.emoji}")
                }
            }
        }
    }
}
