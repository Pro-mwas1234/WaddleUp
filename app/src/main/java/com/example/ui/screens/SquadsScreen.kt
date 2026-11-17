package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AvatarConfig
import com.example.model.FriendNudge
import com.example.model.Mascot
import com.example.model.NudgeType
import com.example.model.Squad
import com.example.model.UiState
import com.example.model.WaddleHumor
import com.example.ui.components.CartoonAvatar
import com.example.ui.components.launchSquadShareSheet

@Composable
fun SquadsScreen(
    squadsState: UiState<List<Squad>>,
    nudgesState: UiState<List<FriendNudge>>,
    isBusy: Boolean,
    onCreateSquad: (String, String, (Squad) -> Unit) -> Unit,
    onJoinSquad: (String) -> Unit,
    onLeaveSquad: (String) -> Unit,
    onAcknowledgeNudge: (String) -> Unit,
    onDeleteNudge: (String) -> Unit,
    onOpenLeaderboardForSquad: (String) -> Unit
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var squadPendingLeave by remember { mutableStateOf<Squad?>(null) }

    val squads = (squadsState as? UiState.Success)?.data ?: emptyList()
    val nudges = (nudgesState as? UiState.Success)?.data ?: emptyList()

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
                Column {
                    Text(
                        text = "Friend Squads & Heckles",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Create a squad, share your 6-letter code, and talk friendly trash.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("create_squad_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Squad")
                    }
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("join_squad_button")
                    ) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join by Code")
                    }
                }
            }

            // Incoming Nudges Inbox
            item {
                Text(
                    text = "Incoming Nudges (${nudges.count { !it.acknowledged }} new)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (nudges.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "Silence so far! Once friends join your squad, they can quack at you here whenever you fall behind.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(nudges, key = { it.id }) { nudge ->
                    val senderMascot = Mascot.fromRaw(nudge.senderMascot)
                    val nudgeType = NudgeType.fromRaw(nudge.nudgeType)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!nudge.acknowledged) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CartoonAvatar(
                                config = AvatarConfig.decode(nudge.senderAvatarCode, senderMascot),
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${nudge.senderName} • ${nudgeType.emoji} ${nudgeType.label}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "\"${nudge.message}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!nudge.acknowledged) {
                                IconButton(
                                    onClick = { onAcknowledgeNudge(nudge.id) },
                                    modifier = Modifier.testTag("ack_nudge_${nudge.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Acknowledge nudge",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteNudge(nudge.id) },
                                modifier = Modifier.testTag("delete_nudge_${nudge.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Dismiss nudge",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Your Squads List
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your Squads (${squads.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (squads.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            text = "Tap \"Create Squad\" above to mint a fresh invite code for your group chat, or enter a friend's 6-character code!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(squads, key = { it.id }) { squad ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = squad.name,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (squad.motto.isNotBlank()) {
                                        Text(
                                            text = "\"${squad.motto}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${squad.memberIds.size} / 25 waddlers",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = squad.inviteCode,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { launchSquadShareSheet(context, squad) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_squad_${squad.inviteCode}")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Invite")
                                }
                                OutlinedButton(
                                    onClick = { onOpenLeaderboardForSquad(squad.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Standings")
                                }
                                IconButton(
                                    onClick = { squadPendingLeave = squad },
                                    modifier = Modifier.testTag("leave_squad_${squad.inviteCode}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Leave squad",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSquadDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, motto ->
                showCreateDialog = false
                onCreateSquad(name, motto) { created ->
                    launchSquadShareSheet(context, created)
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinSquadDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                showJoinDialog = false
                onJoinSquad(code)
            }
        )
    }

    val leaving = squadPendingLeave
    if (leaving != null) {
        AlertDialog(
            onDismissRequest = { squadPendingLeave = null },
            title = { Text("Leave ${leaving.name}?") },
            text = { Text("Your steps will no longer appear on this squad's leaderboard.") },
            confirmButton = {
                Button(onClick = {
                    squadPendingLeave = null
                    onLeaveSquad(leaving.id)
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { squadPendingLeave = null }) {
                    Text("Stay")
                }
            }
        )
    }
}

@Composable
private fun CreateSquadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var motto by remember { mutableStateOf(WaddleHumor.funnySquadMottos.random()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a New Squad", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Squad Name (e.g. Office Waddlers)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_squad_name_input")
                )
                OutlinedTextField(
                    value = motto,
                    onValueChange = { if (it.length <= 80) motto = it },
                    label = { Text("Funny Squad Motto") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_squad_motto_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, motto) },
                enabled = name.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_squad_button")
            ) {
                Text("Create & Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun JoinSquadDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a Friend's Squad", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the 6-character invite code your friend shared with you:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                    },
                    label = { Text("Invite Code (e.g. DUCK42)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_squad_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.length == 6,
                modifier = Modifier.testTag("confirm_join_squad_button")
            ) {
                Text("Join Squad")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
