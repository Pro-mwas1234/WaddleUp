package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AvatarConfig
import com.example.model.Mascot
import com.example.model.NudgeType
import com.example.model.PowerUpType
import com.example.model.Squad
import com.example.model.SquadMember
import com.example.model.UiState
import com.example.model.WaddleHumor
import com.example.ui.LeaderboardPeriod
import com.example.ui.components.CartoonAvatar
import com.example.ui.components.SendNudgeBottomSheet
import com.example.ui.components.formatStepCount
import com.example.ui.components.launchSquadShareSheet

@Composable
fun LeaderboardScreen(
    currentUserId: String,
    squadsState: UiState<List<Squad>>,
    activeSquad: Squad?,
    leaderboardState: UiState<List<SquadMember>>,
    period: LeaderboardPeriod,
    onSelectSquad: (String) -> Unit,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSendNudge: (String, String, String, NudgeType, String?) -> Unit,
    onNavigateToSquads: () -> Unit
) {
    val context = LocalContext.current
    val todayKey = remember { WaddleHumor.todayDateKey() }
    val weekStartKey = remember { WaddleHumor.weekStartDateKey() }

    var nudgeTarget by remember { mutableStateOf<SquadMember?>(null) }

    val squads = (squadsState as? UiState.Success)?.data ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Podium Hero Banner
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_podium_crown_1794947415835),
                            contentDescription = "Golden duck trophy wearing sunglasses on a winner podium",
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Squad Standings",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (activeSquad != null) {
                                    "${activeSquad.name} • Code: ${activeSquad.inviteCode}"
                                } else {
                                    "Create or join a squad to roast your friends!"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (squadsState is UiState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (squads.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Squads Yet!",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Start a squad and text the invite code to your friends—or join theirs—to unlock daily & weekly leaderboards.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateToSquads,
                                modifier = Modifier.testTag("empty_leaderboard_squads_cta")
                            ) {
                                Text("Create or Join a Squad")
                            }
                        }
                    }
                }
            } else {
                // Squad Picker + Invite Button
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            squads.forEach { squad ->
                                FilterChip(
                                    selected = squad.id == activeSquad?.id,
                                    onClick = { onSelectSquad(squad.id) },
                                    label = { Text(squad.name) },
                                    modifier = Modifier.testTag("squad_chip_${squad.inviteCode}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                SegmentedButton(
                                    selected = period == LeaderboardPeriod.DAILY,
                                    onClick = { onSelectPeriod(LeaderboardPeriod.DAILY) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    modifier = Modifier.testTag("period_daily_button")
                                ) {
                                    Text("Daily")
                                }
                                SegmentedButton(
                                    selected = period == LeaderboardPeriod.WEEKLY,
                                    onClick = { onSelectPeriod(LeaderboardPeriod.WEEKLY) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    modifier = Modifier.testTag("period_weekly_button")
                                ) {
                                    Text("Weekly")
                                }
                            }

                            if (activeSquad != null) {
                                Button(
                                    onClick = { launchSquadShareSheet(context, activeSquad) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("leaderboard_share_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.share_squad_cta))
                                }
                            }
                        }
                    }
                }

                when (leaderboardState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is UiState.Error -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    text = leaderboardState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val isDaily = period == LeaderboardPeriod.DAILY
                        val ranked = leaderboardState.data
                            .map { member ->
                                val score = if (isDaily) {
                                    if (member.todayDate == todayKey) member.todaySteps else 0L
                                } else {
                                    if (member.weekStartDate == weekStartKey) member.weeklySteps else 0L
                                }
                                member to score
                            }
                            .sortedByDescending { it.second }

                        itemsIndexed(ranked, key = { _, (m, _) -> m.userId }) { index, (member, score) ->
                            val isMe = member.userId == currentUserId
                            val mascot = Mascot.fromRaw(member.mascot)
                            val avatarConfig = AvatarConfig.decode(member.avatarCode, mascot)
                            val activeBoost = PowerUpType.fromRaw(member.activePowerUp)
                                ?.takeIf { member.powerUpExpiresAtMs > System.currentTimeMillis() }
                            val (badgeEmoji, badgeLabel) = WaddleHumor.leaderboardBadge(
                                rankIndex = index,
                                totalCount = ranked.size,
                                steps = score.toInt(),
                                isDaily = isDaily
                            )
                            val goalBase = if (isDaily) member.dailyGoal.coerceAtLeast(1000L)
                            else (member.dailyGoal * 7L).coerceAtLeast(7000L)
                            val progress = (score.toFloat() / goalBase.toFloat()).coerceIn(0f, 1f)

                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (index == 0) 2.dp else 1.dp,
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(22.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (index == 0) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (index == 0) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        CartoonAvatar(
                                            config = avatarConfig,
                                            size = 50.dp,
                                            animated = activeBoost != null
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isMe) "${member.displayName} (You)" else member.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$badgeEmoji $badgeLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (activeBoost != null) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                val boostColor = Color(activeBoost.accentColorHex)
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = boostColor.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "${activeBoost.emoji} ${activeBoost.title} (${activeBoost.multiplier}x LIVE)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = boostColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (member.bio.isNotBlank()) {
                                                Text(
                                                    text = "\"${member.bio}\"",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = formatStepCount(score),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isDaily) "today" else "this week",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )

                                    if (!isMe && activeSquad != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { nudgeTarget = member }
                                                .testTag("nudge_friend_${member.userId}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Campaign,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Nudge / Heckle ${member.displayName} ${mascot.emoji}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
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
    }

    val target = nudgeTarget
    if (target != null && activeSquad != null) {
        val targetMascot = Mascot.fromRaw(target.mascot)
        SendNudgeBottomSheet(
            recipientName = target.displayName,
            recipientMascot = targetMascot,
            recipientAvatar = AvatarConfig.decode(target.avatarCode, targetMascot),
            onDismiss = { nudgeTarget = null },
            onSend = { type, customMsg ->
                nudgeTarget = null
                onSendNudge(target.userId, target.displayName, activeSquad.id, type, customMsg)
            }
        )
    }
}
