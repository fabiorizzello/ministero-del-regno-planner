package org.example.project.ui.admincatalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import org.example.project.ui.theme.AppTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class AdminResponsiveLayoutTest {
    @Test
    fun `catalog panels remain visible within narrow and wide windows`() {
        for (width in listOf(375, 800, 1200)) {
            for (dark in listOf(false, true)) {
                var sidebar = Rect.Zero
                var detail = Rect.Zero
                val scene = ImageComposeScene(width = width, height = 650) {
                    AppTheme(darkTheme = dark) {
                        AdminToolsShell(
                            sections = adminCatalogSectionItems(AdminCatalogSection.PART_TYPES),
                            onSectionSelected = {},
                        ) {
                            AdminSplitPane(
                                sidebar = {
                                    Box(Modifier.fillMaxSize().onGloballyPositioned { sidebar = it.boundsInRoot() }) {
                                        AdminContentCard("Tipi parte", Modifier.fillMaxSize()) {
                                            Text("Lettura della Bibbia")
                                        }
                                    }
                                },
                                detail = {
                                    Box(Modifier.fillMaxSize().onGloballyPositioned { detail = it.boundsInRoot() }) {
                                        AdminContentCard("Dettaglio", Modifier.fillMaxSize()) {
                                            AdminKeyValueRow("Persone richieste", "1")
                                            AdminKeyValueRow("Nome", "Lettura della Bibbia")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                try {
                    val image = scene.render()
                    for (bounds in listOf(sidebar, detail)) {
                        assertTrue(bounds.width > 0 && bounds.height > 0)
                        assertTrue(bounds.left >= 0 && bounds.top >= 0)
                        assertTrue(bounds.right <= width && bounds.bottom <= 650)
                    }
                    if (width < 940) assertTrue(sidebar.bottom <= detail.top)
                    else assertTrue(sidebar.right <= detail.left)
                    val output = File("build/responsive-preview/admin-$width-${if (dark) "dark" else "light"}.png")
                    output.parentFile.mkdirs()
                    output.writeBytes(image.encodeToData()!!.bytes)
                } finally {
                    scene.close()
                }
            }
        }
    }
}
