package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AvatarAccessory
import com.example.model.AvatarColor
import com.example.model.AvatarConfig
import com.example.model.AvatarFace
import com.example.model.AvatarHair

@Composable
fun AvatarStudioCard(
    avatarConfig: AvatarConfig,
    onAvatarChange: (AvatarConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cartoon Avatar Studio",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Shown on leaderboards & celebrations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onAvatarChange(AvatarConfig.random()) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("avatar_randomize_button")
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chaos")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Cartoon Preview Stage
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CartoonAvatar(
                        config = avatarConfig,
                        size = 104.dp,
                        animated = true
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${avatarConfig.hair.emoji} ${avatarConfig.hair.label}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${avatarConfig.face.emoji} ${avatarConfig.face.label}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${avatarConfig.accessory.emoji} ${avatarConfig.accessory.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body Color Swatches
            Text(
                text = "Body Color",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarColor.entries.forEach { colorOpt ->
                    val selected = colorOpt == avatarConfig.color
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(colorOpt.fillHex))
                            .border(
                                width = if (selected) 3.dp else 1.5.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onAvatarChange(avatarConfig.copy(color = colorOpt)) }
                            .testTag("avatar_color_${colorOpt.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "${colorOpt.label} selected",
                                tint = Color(0xFF1D1633)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hairstyles
            Text(
                text = "Hairstyle",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarHair.entries.forEach { hair ->
                    FilterChip(
                        selected = hair == avatarConfig.hair,
                        onClick = { onAvatarChange(avatarConfig.copy(hair = hair)) },
                        label = { Text("${hair.emoji} ${hair.label}") },
                        modifier = Modifier.testTag("avatar_hair_${hair.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expressive Faces
            Text(
                text = "Expression",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarFace.entries.forEach { face ->
                    FilterChip(
                        selected = face == avatarConfig.face,
                        onClick = { onAvatarChange(avatarConfig.copy(face = face)) },
                        label = { Text("${face.emoji} ${face.label}") },
                        modifier = Modifier.testTag("avatar_face_${face.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Wacky Accessories
            Text(
                text = "Wacky Accessory",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AvatarAccessory.entries.forEach { acc ->
                    FilterChip(
                        selected = acc == avatarConfig.accessory,
                        onClick = { onAvatarChange(avatarConfig.copy(accessory = acc)) },
                        label = { Text("${acc.emoji} ${acc.label}", fontSize = 13.sp) },
                        modifier = Modifier.testTag("avatar_accessory_${acc.name}")
                    )
                }
            }
        }
    }
}
