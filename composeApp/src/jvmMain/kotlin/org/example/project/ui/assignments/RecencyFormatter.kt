package org.example.project.ui.assignments

internal fun formatRecencyLabel(beforeWeeks: Int?, afterWeeks: Int?): String {
    if (beforeWeeks == null && afterWeeks == null) return "Mai assegnato"
    val beforeLabel = beforeWeeks?.let { "$it sett. prima" } ?: "n/d prima"
    val afterLabel = afterWeeks?.let { "$it sett. dopo" } ?: "n/d dopo"
    return "$beforeLabel - $afterLabel"
}
