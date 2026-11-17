package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WaddleRank(
    val title: String,
    val emoji: String,
    val quip: String
)

data class WeirdUnitStat(
    val emoji: String,
    val valueText: String,
    val label: String,
    val subtext: String
)

data class WaddleQuest(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val completed: Boolean
)

data class ManualWalkOption(
    val emoji: String,
    val title: String,
    val steps: Int,
    val joke: String
)

object WaddleHumor {

    val funnyExcuses = listOf(
        "Only walking for iced coffee",
        "My knees make bubble-wrap noises",
        "I thought this was a nap tracker",
        "Chasing a runaway taco truck",
        "100% fueled by spite and snacks",
        "Strutting dramatically to nowhere",
        "Pacing while waiting for pizza",
        "Outrunning my own responsibilities"
    )

    val funnySquadMottos = listOf(
        "We sprint exclusively for free donuts",
        "Zero chill, questionable cardio",
        "Slowly outwalking our own excuses",
        "Powered by snacks and peer pressure",
        "Cardio is hard, roasting friends is easy"
    )

    val manualWalkOptions = listOf(
        ManualWalkOption("🧊", "Fridge Sprint", 250, "Opened the fridge, stared, walked back."),
        ManualWalkOption("🚌", "Bus Chase", 650, "Dignity lost, cardio gained."),
        ManualWalkOption("🛒", "Grocery Wander", 1500, "Entered for milk, left with 4 snacks."),
        ManualWalkOption("🐕", "Dog Walk (Or Imaginary Dog)", 2800, "Very good boy pace."),
        ManualWalkOption("🏃", "Actually Tried Jogging", 4500, "Somebody call the Olympics!")
    )

    fun rankForSteps(steps: Int): WaddleRank = when {
        steps <= 0 -> WaddleRank("Certified Couch Croissant", "🥐", "Zero steps. Blink twice if you're stuck in a blanket.")
        steps < 500 -> WaddleRank("Sleepy Burrito", "🌯", "Brave journey from bed to toaster.")
        steps < 1500 -> WaddleRank("Fridge Sprinter", "🧊", "Elite snack-acquisition footwork.")
        steps < 3000 -> WaddleRank("Reluctant Penguin", "🐧", "Waddling, complaining, yet unstoppable.")
        steps < 5000 -> WaddleRank("Chaotic Pigeon", "🐦", "Zero map, pure vibes, 100% hustle.")
        steps < 7500 -> WaddleRank("Suburban Strutting Duck", "🦆", "Main character energy on the sidewalk.")
        steps < 10000 -> WaddleRank("Turbo Flamingo", "🦩", "Too fast for the neighborhood HOA.")
        steps < 15000 -> WaddleRank("Unstoppable Noodle", "🍜", "Legs are spaghetti, heart is gold.")
        else -> WaddleRank("Supreme Step Goblin", "👑", "Please sit down, you're making us look bad.")
    }

    fun weirdEquivalents(steps: Int): List<WeirdUnitStat> {
        val fridgeTrips = (steps / 18.0).roundToInt()
        val baguettes = ((steps * 0.76) / 0.65).roundToInt()
        val tacosEarned = String.format(Locale.US, "%.1f", (steps * 0.04) / 180.0)
        val snailsLapped = (steps / 25.0).roundToInt()

        return listOf(
            WeirdUnitStat("🧊", "$fridgeTrips", "Fridge Trips", "Round-trip snack runs"),
            WeirdUnitStat("🥖", "$baguettes", "Baguettes Long", "French bakery distance"),
            WeirdUnitStat("🌮", tacosEarned, "Tacos Earned", "Legally guilt-free"),
            WeirdUnitStat("🐌", "$snailsLapped", "Snails Lapped", "Dominance asserted")
        )
    }

    fun leaderboardBadge(rankIndex: Int, totalCount: Int, steps: Int, isDaily: Boolean): Pair<String, String> {
        if (totalCount <= 1) {
            return "👑" to if (isDaily) "Solo Ruler" else "Squad Founder"
        }
        return when {
            rankIndex == 0 && steps > 0 -> "👑" to if (isDaily) "Top Waddle" else "Week Legend"
            rankIndex == 1 && steps > 0 -> "🔥" to "Hot on the Tail"
            rankIndex == totalCount - 1 -> "🛋️" to "Chair Enthusiast"
            steps >= 10000 -> "⚡" to "10K Show-Off"
            else -> "🦆" to "Mid-Pack Waddler"
        }
    }

    fun dailyQuests(todaySteps: Int, nudgesSent: Int, squadCount: Int): List<WaddleQuest> = listOf(
        WaddleQuest(
            id = "q1",
            emoji = "🛏️",
            title = "Escaped Gravity",
            description = "Log at least 100 steps today",
            completed = todaySteps >= 100
        ),
        WaddleQuest(
            id = "q2",
            emoji = "🥐",
            title = "Bakery Commute",
            description = "Cross 2,500 steps today",
            completed = todaySteps >= 2500
        ),
        WaddleQuest(
            id = "q3",
            emoji = "🦆",
            title = "Certified Duck Strut",
            description = "Hit 6,000 steps in a single day",
            completed = todaySteps >= 6000
        ),
        WaddleQuest(
            id = "q4",
            emoji = "📣",
            title = "Friendly Menace",
            description = "Send a playful nudge to a friend",
            completed = nudgesSent >= 1
        ),
        WaddleQuest(
            id = "q5",
            emoji = "👯",
            title = "Squad Up",
            description = "Create or join a friend squad",
            completed = squadCount >= 1
        )
    )

    fun todayDateKey(now: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(now)
    }

    fun weekStartDateKey(now: Date = Date()): String {
        val cal = Calendar.getInstance(Locale.US).apply {
            time = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(cal.time)
    }

    fun last7DaysKeys(now: Date = Date()): List<Pair<String, String>> {
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE", Locale.US)
        val cal = Calendar.getInstance(Locale.US).apply { time = now }
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return (0 until 7).map {
            val d = cal.time
            val pair = keyFmt.format(d) to labelFmt.format(d).take(2)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            pair
        }
    }

    fun currentWeekDateKeys(now: Date = Date()): Set<String> {
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance(Locale.US).apply {
            time = now
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return (0 until 7).map {
            val k = keyFmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            k
        }.toSet()
    }
}
