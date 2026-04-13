package org.example.project.ui.admincatalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class AdminCatalogSection(
    val label: String,
    val tag: String,
    val description: String,
    val icon: ImageVector,
) {
    PART_TYPES(
        label = "Tipi parte",
        tag = "admin-section-tipi-parte",
        description = "Elenco dei tipi di parte con stato e dettagli.",
        icon = Icons.Filled.Category,
    ),
    WEEKLY_SCHEMAS(
        label = "Schemi settimanali",
        tag = "admin-section-schemi-settimanali",
        description = "Settimane disponibili con elenco ordinato delle parti.",
        icon = Icons.Filled.CalendarViewWeek,
    ),
    DIAGNOSTICS(
        label = "Diagnostica",
        tag = "admin-section-diagnostica",
        description = "Controlli tecnici e informazioni di verifica.",
        icon = Icons.Filled.Troubleshoot,
    ),
}

internal data class AdminCatalogSectionItem(
    val section: AdminCatalogSection,
    val selected: Boolean,
)

internal fun adminCatalogSectionItems(
    selectedSection: AdminCatalogSection,
): List<AdminCatalogSectionItem> = AdminCatalogSection.entries.map { section ->
    AdminCatalogSectionItem(
        section = section,
        selected = section == selectedSection,
    )
}

internal fun hasSingleSelectedAdminSection(
    items: List<AdminCatalogSectionItem>,
): Boolean = items.count { it.selected } == 1
