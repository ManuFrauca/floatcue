package io.github.manufrauca.floatcue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun durationCountsEveryLine() {
        assertEquals(60f, durationSeconds(30, 30), 0.001f)
        assertEquals(2f, durationSeconds(1, 30), 0.001f)
        assertEquals(0f, durationSeconds(0, 30), 0f)
    }

    @Test fun roundedDurationSplitsMinutesAndRoundsTo5s() {
        assertEquals(0 to 40, roundedDuration(40f))
        assertEquals(1 to 20, roundedDuration(81f))
        assertEquals(2 to 0, roundedDuration(119f))
        assertEquals(0 to 5, roundedDuration(3f))
        assertEquals(0 to 0, roundedDuration(1f))
    }

    @Test fun sweepCrossesTheLineAsItScrollsPast() {
        // Line from y 100 to 150, text from x 20 to 220, edge 30.
        assertEquals(20f, sweepX(100f, 100f, 150f, 20f, 220f, 30f), 0.001f) // just reached: nothing grey
        assertEquals(135f, sweepX(125f, 100f, 150f, 20f, 220f, 30f), 0.001f) // half-way
        assertEquals(250f, sweepX(150f, 100f, 150f, 20f, 220f, 30f), 0.001f) // scrolled past the line: edge one edge-width past the text's right end
        assertEquals(250f, sweepX(400f, 100f, 150f, 20f, 220f, 30f), 0.001f) // clamped
        assertEquals(250f, sweepX(50f, 100f, 100f, 20f, 220f, 30f), 0.001f) // zero-height line counts as read
    }

    @Test fun sharedTextIgnoresMissingOrBlankTextAndCapsLength() {
        assertNull(sharedText(null, 10)) // e.g. a shared file: no text extra
        assertNull(sharedText(" \n\t", 10))
        assertEquals("Hello", sharedText("Hello", 10))
        assertEquals("abcde", sharedText("abcdefgh", 5))
    }

    @Test fun wordCountIgnoresWhitespaceRuns() {
        assertEquals(4, wordCount("  Hello   brave\nnew\tworld "))
        assertEquals(0, wordCount("  \n "))
    }
}
