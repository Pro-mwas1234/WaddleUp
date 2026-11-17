package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class Mascot(
    val emoji: String,
    val label: String,
    val tagline: String,
    val badgeColorHex: Long
) {
    DUCK("🦆", "Turbo Duck", "Waddling with fierce intent", 0xFFFFE5D9),
    FROG("🐸", "Hop Boss", "One giant leap at a time", 0xFFE6F9B8),
    SNAIL("🐌", "Slow-Mo Snail", "Fast is a social construct", 0xFFEDE9FE),
    PIGEON("🐦", "City Pigeon", "Dodging puddles & crumbs", 0xFFE0F2FE),
    FLAMINGO("🦩", "Strut Flamingo", "Balanced on one questionable knee", 0xFFFCE7F3),
    DINO("🦖", "Tiny-Arm Dino", "Steps big, arms small", 0xFFDCFCE7),
    POTATO("🥔", "Couch Potato", "Reluctantly upright today", 0xFFFEF3C7),
    CRAB("🦀", "Sideways Crab", "Technically still moving forward", 0xFFFEE2E2);

    companion object {
        fun fromRaw(raw: String?): Mascot =
            entries.firstOrNull { it.name == raw } ?: DUCK
    }
}

enum class NudgeType(
    val label: String,
    val emoji: String,
    val defaultMessage: String
) {
    QUACK_ATTACK("Quack Attack", "🦆", "QUACK! Move those sneakers right now!"),
    COUCH_ALERT("Couch Alert", "🛋️", "Your sofa just filed a missing-butt report. Go walk!"),
    SNAIL_PACE("Snail Pace", "🐌", "Breaking news: a garden snail just lapped you."),
    CROWN_THREAT("Crown Threat", "👑", "Watch your back, I'm coming for #1 on the board!"),
    HIGH_FIVE("Victory Honk", "🔥", "Absolute step legend today! Respect the waddle.");

    companion object {
        fun fromRaw(raw: String?): NudgeType =
            entries.firstOrNull { it.name == raw } ?: QUACK_ATTACK
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Waddler",
    val mascot: String = Mascot.DUCK.name,
    val avatarCode: String = "",
    val bio: String = "Only walking for iced coffee",
    val dailyGoal: Long = 8000L,
    val todaySteps: Long = 0L,
    val todayDate: String = "",
    val weeklySteps: Long = 0L,
    val weekStartDate: String = "",
    val bestDailySteps: Long = 0L,
    val activePowerUp: String = "",
    val powerUpExpiresAtMs: Long = 0L,
    val dailyHistory: Map<String, Long> = emptyMap(),
    val totalNudgesSent: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Squad(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val motto: String = "",
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SquadInvite(
    @DocumentId val id: String = "",
    val squadId: String = "",
    val squadName: String = "",
    val creatorId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SquadMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val squadId: String = "",
    val displayName: String = "Waddler",
    val mascot: String = Mascot.DUCK.name,
    val avatarCode: String = "",
    val bio: String = "",
    val dailyGoal: Long = 8000L,
    val todaySteps: Long = 0L,
    val todayDate: String = "",
    val weeklySteps: Long = 0L,
    val weekStartDate: String = "",
    val activePowerUp: String = "",
    val powerUpExpiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendNudge(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderMascot: String = Mascot.DUCK.name,
    val senderAvatarCode: String = "",
    val recipientId: String = "",
    val squadId: String = "",
    val message: String = "",
    val nudgeType: String = NudgeType.QUACK_ATTACK.name,
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
