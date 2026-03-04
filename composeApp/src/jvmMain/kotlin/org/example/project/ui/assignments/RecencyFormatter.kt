package org.example.project.ui.assignments

internal fun formatRecencyLabel(days: Int?, weeks: Int?, inFuture: Boolean): String = when {
    days == null -> "Mai assegnato"
    days == 0 -> "Oggi"
    inFuture && days < 14 -> "Tra $days giorni"
    inFuture -> "Tra ${weeks ?: (days / 7)} settimane"
    days < 14 -> "$days giorni fa"
    else -> "${weeks ?: (days / 7)} settimane fa"
}
