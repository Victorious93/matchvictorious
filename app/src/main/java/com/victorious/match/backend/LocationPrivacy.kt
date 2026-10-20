package com.victorious.match.backend

import kotlin.math.roundToInt

/** Location is reduced to a 0.01° grid (~1.1 km) on the device AND again on the server. */
object LocationPrivacy {
    fun round(v: Double): Double = (v * 100).roundToInt() / 100.0
    fun valid(lat: Double, lon: Double) = lat in -90.0..90.0 && lon in -180.0..180.0
    /** Only an approximate distance is ever shown to other users. */
    fun distanceLabel(km: Int?, city: String?): String? = when {
        km == null -> city
        km <= 1 -> "Less than 1 km away" + (city?.let { " · $it" } ?: "")
        else -> "About $km km away" + (city?.let { " · $it" } ?: "")
    }
}
