package io.github.manufrauca.floatcue

import org.junit.Assert.assertEquals
import org.junit.Test

class PrompterMathTest {
    @Test fun pxPerSecondIsLinesPerMinuteTimesLineHeight() = assertEquals(60f, pxPerSecond(30, 120f), 0.001f)

    @Test fun stepSpeedScalesAndRounds() {
        assertEquals(27, stepSpeed(30, 0.9f))
        assertEquals(33, stepSpeed(30, 1.1f))
    }

    @Test fun stepSpeedClampsAtBounds() {
        assertEquals(SPEED_MIN, stepSpeed(SPEED_MIN, 0.9f))
        assertEquals(SPEED_MAX, stepSpeed(SPEED_MAX, 1.1f))
    }

    @Test fun progressSurvivesAResize() {
        val p = progressOf(250f, 1000f)
        assertEquals(0.25f, p, 0.001f)
        assertEquals(500f, scrollFor(p, 2000f), 0.001f)
    }

    @Test fun progressWithNothingToScrollIsZero() = assertEquals(0f, progressOf(40f, 0f), 0f)

    @Test fun progressIsClamped() {
        assertEquals(1f, progressOf(1500f, 1000f), 0f)
        assertEquals(1000f, scrollFor(2f, 1000f), 0f)
    }

    @Test fun clampYKeepsWindowOnScreen() {
        assertEquals(80, clampY(10, 80, 2000, 600))
        assertEquals(1400, clampY(1900, 80, 2000, 600))
        assertEquals(500, clampY(500, 80, 2000, 600))
    }
}
