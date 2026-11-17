package com.example

import com.example.model.AvatarAccessory
import com.example.model.AvatarColor
import com.example.model.AvatarConfig
import com.example.model.AvatarFace
import com.example.model.AvatarHair
import com.example.model.CelebrationMilestone
import com.example.model.Mascot
import com.example.model.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarAndPowerUpTest {

    @Test
    fun `avatar encode and decode round-trips accurately`() {
        val original = AvatarConfig(
            hair = AvatarHair.POMPADOUR,
            accessory = AvatarAccessory.GOOGLY,
            face = AvatarFace.DERPY,
            color = AvatarColor.BUBBLEGUM
        )
        val encoded = original.encode()
        val decoded = AvatarConfig.decode(encoded, Mascot.DUCK)
        assertEquals(original, decoded)
    }

    @Test
    fun `avatar decode falls back deterministically to mascot preset when blank`() {
        val frogAvatar = AvatarConfig.decode("", Mascot.FROG)
        assertEquals(AvatarHair.SPROUT, frogAvatar.hair)
        assertEquals(AvatarAccessory.GOOGLY, frogAvatar.accessory)
        assertEquals(AvatarColor.LIME, frogAvatar.color)
    }

    @Test
    fun `power-up types define multipliers or instant bursts`() {
        assertEquals(2, PowerUpType.SNAILS_PACE.multiplier)
        assertEquals(5 * 60 * 1000L, PowerUpType.SNAILS_PACE.durationMs)

        assertEquals(3, PowerUpType.ROCKET_BOOST.multiplier)
        assertTrue(PowerUpType.ROCKET_BOOST.instantBonusSteps > 0)

        assertTrue(PowerUpType.SNACK_MAGNET.instantBonusSteps >= 400)
        assertEquals(PowerUpType.DISCO_FEVER, PowerUpType.fromRaw("DISCO_FEVER"))
        assertNull(PowerUpType.fromRaw("UNKNOWN_BOOST"))
    }

    @Test
    fun `celebration milestones have funny non-blank copy`() {
        CelebrationMilestone.entries.forEach { milestone ->
            assertTrue(milestone.headline.isNotBlank())
            assertTrue(milestone.subline.isNotBlank())
            assertTrue(milestone.shareBlurb.isNotBlank())
            assertNotNull(milestone.badgeEmoji)
        }
    }
}
