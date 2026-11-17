package com.example

import com.example.model.WaddleHumor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaddleHumorTest {

    @Test
    fun `rank progresses across step milestones`() {
        assertEquals("Certified Couch Croissant", WaddleHumor.rankForSteps(0).title)
        assertEquals("Fridge Sprinter", WaddleHumor.rankForSteps(1000).title)
        assertEquals("Suburban Strutting Duck", WaddleHumor.rankForSteps(6000).title)
        assertEquals("Supreme Step Goblin", WaddleHumor.rankForSteps(16000).title)
    }

    @Test
    fun `weird equivalents return 4 funny stats`() {
        val stats = WaddleHumor.weirdEquivalents(3600)
        assertEquals(4, stats.size)
        assertTrue(stats.all { it.valueText.isNotBlank() && it.label.isNotBlank() })
    }

    @Test
    fun `leaderboard badges distinguish top and last place`() {
        val first = WaddleHumor.leaderboardBadge(rankIndex = 0, totalCount = 3, steps = 5000, isDaily = true)
        val last = WaddleHumor.leaderboardBadge(rankIndex = 2, totalCount = 3, steps = 200, isDaily = true)
        assertEquals("Top Waddle", first.second)
        assertEquals("Chair Enthusiast", last.second)
    }
}
