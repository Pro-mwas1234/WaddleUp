package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.StepTrackerState
import com.example.model.AvatarConfig
import com.example.model.Mascot
import com.example.model.PowerUpType
import com.example.model.Squad
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.WaddleHumor
import com.example.ui.components.CartoonAvatar
import com.example.ui.components.PowerUpPanel
import com.example.ui.components.WaddleStepRing
import com.example.ui.components.WeeklyBarsChart
import com.example.ui.components.formatStepCount

@Composable
fun TodayScreen(
    profileState: UiState<UserProfile>,
    stepState: StepTrackerState,
    squadsState: UiState<List<Squad>>,
    powerUpRemainingSeconds: Long,
    onStartSensor: () -> Unit,
    onStopSensor: () -> Unit,
    onLogManualWalk: (Int, String) -> Unit,
    onOpenCrate: () -> Unit,
    onActivatePowerUp: (PowerUpType) -> Unit,
    onReplayCelebration: () -> Unit,
    onNavigateToSquads: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    var hasActivityPermission by remember {
        mutableStateOf(
            !needsRuntimePermission || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var showLogWalkDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasActivityPermission = granted
        permissionDenied = !granted
        if (granted) onStartSensor()
    }

    LaunchedEffect(hasActivityPermission) {
        if (hasActivityPermission) {
            onStartSensor()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onStopSensor() }
    }

    val profile = (profileState as? UiState.Success)?.data
    val dailyGoal = profile?.dailyGoal ?: 8000L
    val mascot = Mascot.fromRaw(profile?.mascot)
    val avatarConfig = AvatarConfig.decode(profile?.avatarCode, mascot)
    val totalSteps = stepState.totalTodaySteps
    val bestDailySteps = maxOf(stepState.bestDailySteps, profile?.bestDailySteps ?: 0L, totalSteps)
    val goalReached = totalSteps >= dailyGoal || stepState.weeklySteps >= dailyGoal * 7L
    val rank = WaddleHumor.rankForSteps(totalSteps.toInt())
    val weirdStats = WaddleHumor.weirdEquivalents(totalSteps.toInt())
    val squads = (squadsState as? UiState.Success)?.data ?: emptyList()
    val quests = WaddleHumor.dailyQuests(
        todaySteps = totalSteps.toInt(),
        nudgesSent = (profile?.totalNudgesSent ?: 0L).toInt(),
        squadCount = squads.size
    )

    val last7Days = remember(stepState.dailyHistory, stepState.todayDate, totalSteps) {
        WaddleHumor.last7DaysKeys().map { (dateKey, dayLabel) ->
            val v = if (dateKey == stepState.todayDate) totalSteps else (stepState.dailyHistory[dateKey] ?: 0L)
            dayLabel to v
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onNavigateToProfile)
                            .testTag("today_avatar_button")
                    ) {
                        CartoonAvatar(config = avatarConfig, size = 58.dp, animated = true)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hey, ${profile?.displayName ?: "Waddler"}!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "\"${profile?.bio?.ifBlank { mascot.tagline } ?: mascot.tagline}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onNavigateToSquads)
                    ) {
                        Text(
                            text = "${squads.size} Squad${if (squads.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Runtime Permission Card (if needed)
            if (needsRuntimePermission && !hasActivityPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = stringResource(R.string.permission_steps_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (permissionDenied) {
                                    "Physical Activity permission was denied. Enable it in Settings or tap below to retry—or log walks manually anytime!"
                                } else {
                                    stringResource(R.string.permission_steps_desc)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                },
                                modifier = Modifier.testTag("enable_step_sensor_button")
                            ) {
                                Icon(Icons.Filled.Sensors, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.permission_steps_cta))
                            }
                        }
                    }
                }
            }

            // Hero Step Ring Card
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (stepState.sensorActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sensors,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (stepState.sensorActive) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (stepState.sensorActive) "${stepState.sensorName} Live"
                                    else stepState.sensorName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        WaddleStepRing(
                            steps = totalSteps,
                            goal = dailyGoal,
                            rankEmoji = rank.emoji,
                            rankTitle = rank.title
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = rank.quip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (stepState.manualStepsToday > 0L || stepState.sensorStepsToday > 0L || stepState.powerUpBonusStepsToday > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val parts = buildList {
                                if (stepState.sensorStepsToday > 0L) add("${formatStepCount(stepState.sensorStepsToday)} sensor")
                                if (stepState.manualStepsToday > 0L) add("${formatStepCount(stepState.manualStepsToday)} logged")
                                if (stepState.powerUpBonusStepsToday > 0L) add("+${formatStepCount(stepState.powerUpBonusStepsToday)} power-up")
                            }
                            Text(
                                text = parts.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "👑 Best: ${formatStepCount(bestDailySteps)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            if (goalReached) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onReplayCelebration)
                                        .testTag("replay_celebration_button")
                                ) {
                                    Text(
                                        text = "🎉 Dance Party!",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLogWalkDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("log_walk_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.log_walk_cta))
                            }
                            OutlinedButton(
                                onClick = onNavigateToSquads,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("today_squads_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Squad Up")
                            }
                        }
                    }
                }
            }

            // Power-Up Steps & Mystery Crate Panel
            item {
                PowerUpPanel(
                    activePowerUp = stepState.activePowerUp,
                    remainingSeconds = powerUpRemainingSeconds,
                    bonusStepsToday = stepState.powerUpBonusStepsToday,
                    inventory = stepState.powerUpInventory,
                    unclaimedCrates = stepState.unclaimedCrates,
                    onOpenCrate = onOpenCrate,
                    onActivatePowerUp = onActivatePowerUp
                )
            }

            // Funny Zero-Step Potato Card when steps == 0
            if (totalSteps == 0L) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_couch_potato_1794947416361),
                                contentDescription = "Sleepy couch potato staring at a lonely sneaker",
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "The couch has claimed you!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Walk around with your phone or tap \"Log a Walk\" for indoor treadmill & snack runs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Weird Step Translations Grid
            item {
                Column {
                    Text(
                        text = "Your Steps in Ridiculous Units",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WeirdStatCard(weirdStats[0], Modifier.weight(1f))
                        WeirdStatCard(weirdStats[1], Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WeirdStatCard(weirdStats[2], Modifier.weight(1f))
                        WeirdStatCard(weirdStats[3], Modifier.weight(1f))
                    }
                }
            }

            // 7-Day Weekly Chart
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "This Week's Waddle",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "Last 7 days • Green bars = goal crushed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "${formatStepCount(stepState.weeklySteps)} wk",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        WeeklyBarsChart(days = last7Days, goal = dailyGoal)
                    }
                }
            }

            // Silly Daily Quests
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Daily Silly Quests",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        quests.forEach { quest ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (quest.completed) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = quest.emoji, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = quest.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = quest.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (quest.completed) Icons.Filled.CheckCircle
                                    else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (quest.completed) "Completed" else "Incomplete",
                                    tint = if (quest.completed) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogWalkDialog) {
        LogWalkDialog(
            onDismiss = { showLogWalkDialog = false },
            onLog = { steps, label ->
                showLogWalkDialog = false
                onLogManualWalk(steps, label)
            }
        )
    }
}

@Composable
private fun WeirdStatCard(
    stat: com.example.model.WeirdUnitStat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stat.emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stat.valueText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stat.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stat.subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogWalkDialog(
    onDismiss: () -> Unit,
    onLog: (Int, String) -> Unit
) {
    var customStepsText by remember { mutableStateOf("") }
    var customLabel by remember { mutableStateOf("Treadmill Strut") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log a Walk (Phone Left Behind?)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pick a funny walk preset or log your own steps:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WaddleHumor.manualWalkOptions.forEach { opt ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLog(opt.steps, opt.title) }
                            .testTag("walk_preset_${opt.steps}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = opt.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = opt.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = opt.joke,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "+${opt.steps}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customStepsText,
                    onValueChange = { input ->
                        customStepsText = input.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Custom steps (e.g. 1200)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_walk_steps_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val steps = customStepsText.toIntOrNull() ?: 0
                    if (steps in 1..50000) {
                        onLog(steps, customLabel)
                    }
                },
                enabled = (customStepsText.toIntOrNull() ?: 0) in 1..50000,
                modifier = Modifier.testTag("confirm_custom_walk_button")
            ) {
                Text("Add Steps")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
