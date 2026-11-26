package dk.brugtbil.browser.ui

import java.text.NumberFormat
import java.util.Locale

private val danish: NumberFormat = NumberFormat.getIntegerInstance(Locale("da", "DK"))

fun kr(n: Int): String = "${danish.format(n)} kr."
fun km(n: Int): String = "${danish.format(n)} km"

fun range(min: Int?, max: Int?, fmt: (Int) -> String): String? = when {
    min == null || max == null -> null
    min == max -> fmt(min)
    else -> "${fmt(min)} – ${fmt(max)}"
}
