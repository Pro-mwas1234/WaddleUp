package com.example.model

import kotlin.random.Random

enum class AvatarColor(val label: String, val fillHex: Long) {
    TANGERINE("Tangerine", 0xFFFF8A5B),
    LIME("Volt Lime", 0xFFB8F22E),
    BUBBLEGUM("Bubblegum", 0xFFF472B6),
    SKY("Sky Pop", 0xFF38BDF8),
    GRAPE("Grape Fizz", 0xFFA78BFA),
    BANANA("Banana", 0xFFFDE047),
    MINT("Minty", 0xFF34D399),
    POTATO("Toasty", 0xFFE6B88A);

    companion object {
        fun fromRaw(raw: String?): AvatarColor =
            entries.firstOrNull { it.name == raw } ?: TANGERINE
    }
}

enum class AvatarHair(val label: String, val emoji: String) {
    TUFT("Duck Tuft", "🐤"),
    MOHAWK("Punk Mohawk", "🤘"),
    AFRO("Fluffy Cloud", "☁️"),
    SPROUT("Leaf Sprout", "🌱"),
    CURLS("Chaos Curls", "🌀"),
    POMPADOUR("Elvis Wave", "🎸"),
    BALD("Aero Bald", "🥚");

    companion object {
        fun fromRaw(raw: String?): AvatarHair =
            entries.firstOrNull { it.name == raw } ?: TUFT
    }
}

enum class AvatarFace(val label: String, val emoji: String) {
    GRIN("Unhinged Grin", "😁"),
    HYPED("Star-Struck", "🤩"),
    DERPY("Derpy Tongue", "😜"),
    SMUG("Too Cool", "😏"),
    DIZZY("Spinny Dizzy", "😵"),
    WINK("Cheeky Wink", "😉");

    companion object {
        fun fromRaw(raw: String?): AvatarFace =
            entries.firstOrNull { it.name == raw } ?: GRIN
    }
}

enum class AvatarAccessory(val label: String, val emoji: String) {
    GOOGLY("Googly Eyes", "👀"),
    TINY_HAT("Tiny Top Hat", "🎩"),
    PROPELLER("Propeller Cap", "🧢"),
    SHADES("Cool Shades", "🕶️"),
    CROWN("Paper Crown", "👑"),
    SWEATBAND("80s Sweatband", "🏋️"),
    MONOCLE("Fancy Monocle", "🧐"),
    NONE("No Extras", "✨");

    companion object {
        fun fromRaw(raw: String?): AvatarAccessory =
            entries.firstOrNull { it.name == raw } ?: GOOGLY
    }
}

data class AvatarConfig(
    val hair: AvatarHair = AvatarHair.TUFT,
    val accessory: AvatarAccessory = AvatarAccessory.GOOGLY,
    val face: AvatarFace = AvatarFace.GRIN,
    val color: AvatarColor = AvatarColor.TANGERINE
) {
    fun encode(): String = "${hair.name}:${accessory.name}:${face.name}:${color.name}"

    companion object {
        fun decode(raw: String?, fallbackMascot: Mascot = Mascot.DUCK): AvatarConfig {
            if (!raw.isNullOrBlank()) {
                val parts = raw.split(":")
                if (parts.size == 4) {
                    return AvatarConfig(
                        hair = AvatarHair.fromRaw(parts[0]),
                        accessory = AvatarAccessory.fromRaw(parts[1]),
                        face = AvatarFace.fromRaw(parts[2]),
                        color = AvatarColor.fromRaw(parts[3])
                    )
                }
            }
            return when (fallbackMascot) {
                Mascot.DUCK -> AvatarConfig(AvatarHair.TUFT, AvatarAccessory.SWEATBAND, AvatarFace.GRIN, AvatarColor.BANANA)
                Mascot.FROG -> AvatarConfig(AvatarHair.SPROUT, AvatarAccessory.GOOGLY, AvatarFace.DERPY, AvatarColor.LIME)
                Mascot.SNAIL -> AvatarConfig(AvatarHair.CURLS, AvatarAccessory.MONOCLE, AvatarFace.SMUG, AvatarColor.GRAPE)
                Mascot.PIGEON -> AvatarConfig(AvatarHair.MOHAWK, AvatarAccessory.SHADES, AvatarFace.WINK, AvatarColor.SKY)
                Mascot.FLAMINGO -> AvatarConfig(AvatarHair.POMPADOUR, AvatarAccessory.CROWN, AvatarFace.HYPED, AvatarColor.BUBBLEGUM)
                Mascot.DINO -> AvatarConfig(AvatarHair.MOHAWK, AvatarAccessory.PROPELLER, AvatarFace.GRIN, AvatarColor.MINT)
                Mascot.POTATO -> AvatarConfig(AvatarHair.BALD, AvatarAccessory.TINY_HAT, AvatarFace.DIZZY, AvatarColor.POTATO)
                Mascot.CRAB -> AvatarConfig(AvatarHair.AFRO, AvatarAccessory.GOOGLY, AvatarFace.DERPY, AvatarColor.TANGERINE)
            }
        }

        fun random(): AvatarConfig = AvatarConfig(
            hair = AvatarHair.entries[Random.nextInt(AvatarHair.entries.size)],
            accessory = AvatarAccessory.entries[Random.nextInt(AvatarAccessory.entries.size)],
            face = AvatarFace.entries[Random.nextInt(AvatarFace.entries.size)],
            color = AvatarColor.entries[Random.nextInt(AvatarColor.entries.size)]
        )
    }
}

enum class PowerUpType(
    val title: String,
    val emoji: String,
    val badgeText: String,
    val description: String,
    val durationMs: Long,
    val multiplier: Int,
    val instantBonusSteps: Int,
    val accentColorHex: Long
) {
    SNAILS_PACE(
        title = "Snail's Pace",
        emoji = "🐌",
        badgeText = "2x STEPS • 5 MIN",
        description = "Channel pure slime energy: every step counts DOUBLE for 5 minutes!",
        durationMs = 5 * 60 * 1000L,
        multiplier = 2,
        instantBonusSteps = 0,
        accentColorHex = 0xFF7C3AED
    ),
    ROCKET_BOOST(
        title = "Rocket Boost",
        emoji = "🚀",
        badgeText = "+250 & 3x • 2 MIN",
        description = "Strap fireworks to your sneakers: +250 instant steps + 3x burst for 2 mins!",
        durationMs = 2 * 60 * 1000L,
        multiplier = 3,
        instantBonusSteps = 250,
        accentColorHex = 0xFFFF5A1F
    ),
    DISCO_FEVER(
        title = "Disco Fever",
        emoji = "🪩",
        badgeText = "+150 & 2x • 3 MIN",
        description = "Boogie down the sidewalk: +150 groove steps + 2x steps for 3 minutes!",
        durationMs = 3 * 60 * 1000L,
        multiplier = 2,
        instantBonusSteps = 150,
        accentColorHex = 0xFFEC4899
    ),
    SNACK_MAGNET(
        title = "Snack Magnet",
        emoji = "🌮",
        badgeText = "+400 INSTANT",
        description = "Someone opened tacos nearby! Instant +400 turbo sprint steps.",
        durationMs = 0L,
        multiplier = 1,
        instantBonusSteps = 400,
        accentColorHex = 0xFF16A34A
    );

    companion object {
        fun fromRaw(raw: String?): PowerUpType? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class CelebrationMilestone(
    val headline: String,
    val subline: String,
    val badgeEmoji: String,
    val shareBlurb: String
) {
    DAILY_GOAL(
        headline = "DAILY GOAL OBLITERATED!",
        subline = "Your couch just filed a missing-person report. Keep dancing!",
        badgeEmoji = "🎯",
        shareBlurb = "Just crushed my daily step goal on WaddleUp! 🦆🔥"
    ),
    WEEKLY_TARGET(
        headline = "WEEKLY TARGET SMASHED!",
        subline = "Scientists are baffled by your unstoppable week-long strut!",
        badgeEmoji = "🏆",
        shareBlurb = "Just smashed my WEEKLY step target on WaddleUp! 🏆⚡"
    ),
    PERSONAL_BEST(
        headline = "NEW PERSONAL BEST!",
        subline = "Yesterday-You is watching in slow motion with a single tear of pride!",
        badgeEmoji = "👑",
        shareBlurb = "New personal record steps today on WaddleUp! 👑🚀"
    )
}

data class CelebrationEvent(
    val milestone: CelebrationMilestone,
    val stepValue: Long
)
