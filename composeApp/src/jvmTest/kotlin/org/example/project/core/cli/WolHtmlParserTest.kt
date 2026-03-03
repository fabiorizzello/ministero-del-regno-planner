package org.example.project.core.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WolHtmlParserTest {
    @Test
    fun `next week url rollover from week 52 switches year`() {
        val next = nextWeekUrlFromMeetingsUrl("https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/52")
        assertEquals("https://wol.jw.org/it/wol/meetings/r6/lp-i/2027/01", next)
    }

    @Test
    fun `next week url increments within same year`() {
        val next = nextWeekUrlFromMeetingsUrl("https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/09")
        assertEquals("https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/10", next)
    }

    @Test
    fun `parse current week meetings url from todayWeek selector`() {
        val html = """
            <html>
              <body>
                <li id="navigationDailyTextToday" class="todayNav">
                  <a href="/it/wol/meetings/r6/lp-i">Questa settimana</a>
                </li>
                <input id="todayWeek" value="2026/9"/>
              </body>
            </html>
        """.trimIndent()

        val currentWeekUrl = WolHtmlParser.parseCurrentWeekMeetingsUrl(
            html = html,
            baseUrl = "https://wol.jw.org/it/wol/meetings/r6/lp-i",
        )

        assertEquals("https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/09", currentWeekUrl)
    }

    @Test
    fun `parse current week meetings url returns null without todayWeek`() {
        val html = """
            <html>
              <body>
                <li id="navigationDailyTextToday" class="todayNav">
                  <a href="/it/wol/meetings/r6/lp-i">Questa settimana</a>
                </li>
              </body>
            </html>
        """.trimIndent()

        val currentWeekUrl = WolHtmlParser.parseCurrentWeekMeetingsUrl(
            html = html,
            baseUrl = "https://wol.jw.org/it/wol/meetings/r6/lp-i",
        )

        assertNull(currentWeekUrl)
    }

    @Test
    fun `parse meetings week page extracts week date, vita link and next week link`() {
        val html = """
            <html>
              <body>
                <input id="shareBaseUrl" value="https://www.jw.org/finder?wtlocale=I&amp;alias=meetings&amp;date=2026-02-23&amp;srctype=wol"/>
                <div id="materialNav">
                  <h2>Vita e ministero</h2>
                  <ul>
                    <li class="pub-mwb">
                      <a href="/it/wol/d/r6/lp-i/202026008">settimana</a>
                    </li>
                  </ul>
                </div>
                <li id="footerNextWeek">
                  <a href="/it/wol/meetings/r6/lp-i/2026/10">next</a>
                </li>
              </body>
            </html>
        """.trimIndent()

        val page = WolHtmlParser.parseMeetingsWeekPage(
            html = html,
            baseUrl = "https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/09",
        )

        assertNotNull(page)
        assertEquals("2026-02-23", page.weekStartDate.toString())
        assertEquals("https://wol.jw.org/it/wol/d/r6/lp-i/202026008", page.vitaMinisteroUrl)
        assertEquals("https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/10", page.nextWeekUrl)
    }

    @Test
    fun `parse meetings week page keeps week date when vita link is missing`() {
        val html = """
            <html>
              <body>
                <input id="shareBaseUrl" value="https://www.jw.org/finder?alias=meetings&amp;date=2026-02-23"/>
                <div id="materialNav"><h2>Vita e ministero</h2></div>
              </body>
            </html>
        """.trimIndent()

        val page = WolHtmlParser.parseMeetingsWeekPage(
            html = html,
            baseUrl = "https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/09",
        )

        assertNotNull(page)
        assertEquals("2026-02-23", page.weekStartDate.toString())
        assertNull(page.vitaMinisteroUrl)
    }

    @Test
    fun `parse meetings week page does not use non-mwb links when vita program missing`() {
        val html = """
            <html>
              <body>
                <input id="shareBaseUrl" value="https://www.jw.org/finder?alias=meetings&amp;date=2026-12-28"/>
                <div id="materialNav">
                  <h2>Vita e ministero</h2>
                  <ul>
                    <li><a href="/it/wol/d/r6/lp-i/2025685">Torre di Guardia</a></li>
                  </ul>
                </div>
              </body>
            </html>
        """.trimIndent()

        val page = WolHtmlParser.parseMeetingsWeekPage(
            html = html,
            baseUrl = "https://wol.jw.org/it/wol/meetings/r6/lp-i/2026/52",
        )

        assertNotNull(page)
        assertEquals("2026-12-28", page.weekStartDate.toString())
        assertNull(page.vitaMinisteroUrl)
    }

    @Test
    fun `parse efficaci section parts extracts numbered h3 until next h2`() {
        val html = """
            <html>
              <body>
                <h2>TESORI DELLA PAROLA DI DIO</h2>
                <h3>3. Lettura biblica</h3>
                <h2>EFFICACI NEL MINISTERO</h2>
                <h3>4. Iniziare una conversazione</h3>
                <h3>5. Iniziare una conversazione</h3>
                <h3>6. Coltivare l’interesse</h3>
                <h3>7. Fare discepoli</h3>
                <h2>VITA CRISTIANA</h2>
                <h3>8. Rapporto di servizio annuale</h3>
              </body>
            </html>
        """.trimIndent()

        val parts = WolHtmlParser.parseEfficaciSectionParts(html)

        assertEquals(4, parts.size)
        assertEquals(4, parts[0].number)
        assertEquals("Iniziare una conversazione", parts[0].title)
        assertEquals(5, parts[1].number)
        assertEquals("Iniziare una conversazione", parts[1].title)
        assertEquals(6, parts[2].number)
        assertEquals("Coltivare l’interesse", parts[2].title)
        assertEquals(7, parts[3].number)
        assertEquals("Fare discepoli", parts[3].title)
    }
}
