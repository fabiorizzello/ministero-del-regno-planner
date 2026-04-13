package org.example.project.ui.admincatalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.example.project.ui.diagnostics.DiagnosticsScreen

@Composable
internal fun AdminToolsScreen(
    modifier: Modifier = Modifier,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(AdminCatalogSection.PART_TYPES.name) }
    val selectedSection = AdminCatalogSection.entries.firstOrNull { it.name == selectedSectionName }
        ?: AdminCatalogSection.PART_TYPES

    AdminToolsShell(
        modifier = modifier,
        sections = adminCatalogSectionItems(selectedSection),
        onSectionSelected = { section -> selectedSectionName = section.name },
    ) {
        when (selectedSection) {
            AdminCatalogSection.DIAGNOSTICS -> DiagnosticsScreen()
            AdminCatalogSection.PART_TYPES -> PartTypeCatalogScreen()
            AdminCatalogSection.WEEKLY_SCHEMAS -> WeeklySchemaCatalogScreen()
        }
    }
}
