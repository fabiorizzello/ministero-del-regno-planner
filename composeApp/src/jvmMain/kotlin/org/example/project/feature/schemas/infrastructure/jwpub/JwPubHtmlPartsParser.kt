package org.example.project.feature.schemas.infrastructure.jwpub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

enum class JwPubSection { HEADER, TESORI, EFFICACI, VITA_CRISTIANA, UNKNOWN }

data class JwPubPart(
    val pid: String,
    val title: String,
    val detailLine: String?,
    val section: JwPubSection,
    val sortOrder: Int,
)

class JwPubHtmlPartsParser {

    fun parseParts(html: String): List<JwPubPart> {
        val doc = Jsoup.parse(html)
        val root = doc.body()
        val result = mutableListOf<JwPubPart>()
        var currentSection = JwPubSection.HEADER
        var sortOrder = 0

        for (element in root.allElements) {
            when (element.tagName()) {
                "h2" -> currentSection = classifySection(element.text())
                "h3" -> {
                    val id = element.id()
                    if (id.startsWith("p")) {
                        val title = cleanText(element.text())
                        if (title.isNotBlank()) {
                            val detailLine = findDetailLine(element)
                            val section = refineSection(currentSection, title)
                            result += JwPubPart(
                                pid = id,
                                title = title,
                                detailLine = detailLine,
                                section = section,
                                sortOrder = sortOrder++,
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    private fun refineSection(current: JwPubSection, title: String): JwPubSection {
        // "Lettura biblica" appears under the TESORI <h2> in the HTML but by
        // convention belongs to the EFFICACI NEL MINISTERO section.
        if (current == JwPubSection.TESORI && title.contains("Lettura biblica", ignoreCase = true)) {
            return JwPubSection.EFFICACI
        }
        return current
    }

    private fun classifySection(text: String): JwPubSection {
        val upper = text.trim().uppercase()
        return when {
            "TESORI" in upper -> JwPubSection.TESORI
            "EFFICACI" in upper -> JwPubSection.EFFICACI
            "VITA" in upper -> JwPubSection.VITA_CRISTIANA
            else -> JwPubSection.UNKNOWN
        }
    }

    private fun findDetailLine(h3: Element): String? {
        var sibling = h3.nextElementSibling()
        while (sibling != null) {
            val p = when {
                sibling.tagName() == "p" -> sibling
                sibling.tagName() == "div" -> sibling.selectFirst("p")
                else -> null
            }
            if (p != null) {
                val text = cleanText(p.text())
                if (text.isNotBlank()) return text
            }
            sibling = sibling.nextElementSibling()
        }
        return null
    }

    private fun cleanText(value: String): String =
        value.replace(' ', ' ').replace('’', '\'').replace(Regex("\\s+"), " ").trim()
}
