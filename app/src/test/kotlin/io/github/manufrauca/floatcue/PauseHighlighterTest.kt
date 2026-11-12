package io.github.manufrauca.floatcue

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseHighlighterTest {
    private fun matches(t: String) = pauseRanges(t).map { t.substring(it) }

    @Test fun bareWordsAnyCase() = assertEquals(listOf("pause", "PAUSA", "Pause"), matches("pause then PAUSA and Pause"))

    @Test fun bracketsIncluded() = assertEquals(listOf("[pause]", "[Pausa]"), matches("Hello [pause] world [Pausa]."))

    @Test fun punctuationExcluded() = assertEquals(listOf("PAUSA"), matches("Stop. PAUSA, go"))

    @Test fun notInsideOtherWords() = assertEquals(emptyList<String>(), matches("pausado pauses repausa pausar unpause pause1 pause_x"))

    @Test fun accentedNeighbourIsNotAMatch() = assertEquals(emptyList<String>(), matches("pausá ápausa"))

    @Test fun rangesAreCorrect() = assertEquals(listOf(6..12), pauseRanges("Hello [pause]!"))

    @Test fun emptyText() = assertEquals(emptyList<IntRange>(), pauseRanges(""))
}
