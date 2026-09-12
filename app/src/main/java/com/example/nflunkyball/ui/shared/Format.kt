package com.example.nflunkyball.ui.shared

import java.util.Locale

/** Ratings and second-averages are shown with one decimal everywhere, locale-independent. */
fun Double.format1(): String = String.format(Locale.US, "%.1f", this)

/** "+12.3" / "-4.0" — for Elo deltas. */
fun Double.formatSigned1(): String = (if (this >= 0) "+" else "") + format1()
