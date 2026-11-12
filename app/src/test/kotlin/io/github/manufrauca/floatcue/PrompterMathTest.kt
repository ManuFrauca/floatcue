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

    @Test fun easeTowardMovesPartWayAndConverges() {
        assertEquals(0f, easeToward(0f, 100f, 0f, 0.22f), 0f)
        assertEquals(63.2f, easeToward(0f, 100f, 0.22f, 0.22f), 0.1f)
        assertEquals(100f, easeToward(0f, 100f, 10f, 0.22f), 0.01f)
        assertEquals(40f, easeToward(100f, 40f, 0f, 0f) , 0f)
        assertEquals(0f, easeToward(50f, 0f, 5f, 0.22f), 0.01f)
    }

    @Test fun durationUsesLinesMinusOne() {
        assertEquals(60f, durationSeconds(31, 30), 0.001f)
        assertEquals(0f, durationSeconds(1, 30), 0f)
        assertEquals(0f, durationSeconds(0, 30), 0f)
    }

    @Test fun durationLabelFormats() {
        assertEquals("about 40 s", durationLabel(40f))
        assertEquals("about 1 min 20 s", durationLabel(81f))
        assertEquals("about 2 min", durationLabel(119f))
        assertEquals("about 5 s", durationLabel(3f))
        assertEquals("less than 5 s", durationLabel(1f))
    }

    @Test fun wordCountIgnoresWhitespaceRuns() {
        assertEquals(4, wordCount("  Hello   brave\nnew\tworld "))
        assertEquals(0, wordCount("  \n "))
    }
}
