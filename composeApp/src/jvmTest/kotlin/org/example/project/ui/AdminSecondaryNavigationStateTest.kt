package org.example.project.ui

import org.example.project.ui.admincatalog.AdminCatalogSection
import org.example.project.ui.admincatalog.adminCatalogSectionItems
import org.example.project.ui.admincatalog.hasSingleSelectedAdminSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminSecondaryNavigationStateTest {

    @Test
    fun `adminCatalogSectionItems mark only one selected section`() {
        val items = adminCatalogSectionItems(AdminCatalogSection.PART_TYPES)

        assertTrue(hasSingleSelectedAdminSection(items))
        assertEquals(AdminCatalogSection.PART_TYPES, items.single { it.selected }.section)
    }

    @Test
    fun `isAdminToolbarSelected is true only for diagnostics section wrapper`() {
        assertTrue(isAdminToolbarSelected(AppSection.DIAGNOSTICS))
        assertTrue(!isAdminToolbarSelected(AppSection.PLANNING))
        assertTrue(!isAdminToolbarSelected(AppSection.PROCLAMATORI))
    }
}
