package io.github.manufrauca.floatcue

private val PAUSE = Regex(
    "(?<![\\p{L}\\p{M}\\p{N}_])\\[?(?:pause|pausa)]?(?![\\p{L}\\p{M}\\p{N}_])",
    RegexOption.IGNORE_CASE,
)

/** Ranges of whole-word "pause"/"pausa" (any case), including surrounding square brackets if present. */
fun pauseRanges(text: String): List<IntRange> = PAUSE.findAll(text).map { it.range }.toList()
