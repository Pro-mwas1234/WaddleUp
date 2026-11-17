package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.signOut
import com.example.data.StepTrackerState
import com.example.model.AvatarConfig
import com.example.model.Mascot
import com.example.model.Squad
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.WaddleHumor
import com.example.ui.components.AvatarStudioCard
import com.example.ui.components.CartoonAvatar
import com.example.ui.components.formatStepCount
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profileState: UiState<UserProfile>,
    stepState: StepTrackerState,
    squadsState: UiState<List<Squad>>,
    isBusy: Boolean,
    onSaveProfile: (String, Mascot, AvatarConfig, String, Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile = (profileState as? UiState.Success)?.data
    val squads = (squadsState as? UiState.Success)?.data ?: emptyList()

    var nickname by remember(profile?.userId) { mutableStateOf(profile?.displayName ?: "Waddler") }
    var selectedMascot by remember(profile?.userId) { mutableStateOf(Mascot.fromRaw(profile?.mascot)) }
    var selectedAvatar by remember(profile?.userId) {
        mutableStateOf(AvatarConfig.decode(profile?.avatarCode, Mascot.fromRaw(profile?.mascot)))
    }
    var bio by remember(profile?.userId) { mutableStateOf(profile?.bio ?: WaddleHumor.funnyExcuses.first()) }
    var goalFloat by remember(profile?.userId) { mutableFloatStateOf((profile?.dailyGoal ?: 8000L).toFloat()) }

    LaunchedEffect(profile) {
        if (profile != null) {
            nickname = profile.displayName
            val m = Mascot.fromRaw(profile.mascot)
            selectedMascot = m
            selectedAvatar = AvatarConfig.decode(profile.avatarCode, m)
            bio = profile.bio
            goalFloat = profile.dailyGoal.toFloat()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CartoonAvatar(config = selectedAvatar, size = 68.dp, animated = true)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nickname.ifBlank { "Waddler" },
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "${selectedMascot.label} • ${selectedMascot.tagline}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Stats Strip
            item {
                val bestDay = maxOf(stepState.bestDailySteps, profile?.bestDailySteps ?: 0L, stepState.totalTodaySteps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniStatCard("Weekly", formatStepCount(stepState.weeklySteps), Modifier.weight(1f))
                    MiniStatCard("Best Day", formatStepCount(bestDay), Modifier.weight(1f))
                    MiniStatCard("Squads", "${squads.size}", Modifier.weight(1f))
                }
            }

            // Cartoon Avatar Designer Studio
            item {
                AvatarStudioCard(
                    avatarConfig = selectedAvatar,
                    onAvatarChange = { selectedAvatar = it }
                )
            }

            // Mascot Picker
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pick Your Spirit Waddle",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Mascot.entries.forEach { mascot ->
                                val isSelected = mascot == selectedMascot
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color(mascot.badgeColorHex).copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { selectedMascot = mascot }
                                        .testTag("mascot_option_${mascot.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = mascot.emoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = mascot.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Identity & Goal Form
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Waddle Persona & Daily Goal",
                            style = MaterialTheme.typography.titleLarge
                        )

                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { if (it.length <= 30) nickname = it },
                            label = { Text("Nickname shown to friends") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_nickname_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 80) bio = it },
                            label = { Text("Funny Walking Excuse / Bio") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = { bio = WaddleHumor.funnyExcuses.random() }) {
                                    Icon(Icons.Filled.Casino, contentDescription = "Random funny excuse")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_input")
                        )

                        Column {
                            val snappedGoal = ((goalFloat / 500f).roundToLong() * 500L).coerceIn(2000L, 20000L)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Step Goal", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${formatStepCount(snappedGoal)} steps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = goalFloat,
                                onValueChange = { goalFloat = it },
                                valueRange = 2000f..20000f,
                                steps = 35
                            )
                        }

                        Button(
                            onClick = {
                                val snappedGoal = ((goalFloat / 500f).roundToLong() * 500L).coerceIn(2000L, 20000L)
                                onSaveProfile(nickname, selectedMascot, selectedAvatar, bio, snappedGoal)
                            },
                            enabled = !isBusy && nickname.trim().isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_profile_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Waddle Persona")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        signOut(credentialManager = credentialManager, scope = scope)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sign_out),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
