package org.example.project.feature.schemas.infrastructure.jwpub

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

data class JwPubWeekRow(
    val documentId: Int,
    val mepsDocumentId: Long,
    val title: String,
    val subtitle: String?,
    val content: ByteArray,
)

class JwPubSqliteReader {

    fun readPubCard(dbFile: Path): PubCard = withConnection(dbFile) { conn ->
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT MepsLanguageIndex, Symbol, Year, IssueTagNumber FROM Publication LIMIT 1",
            ).use { rs ->
                if (!rs.next()) throw IllegalStateException("Publication table empty in $dbFile")
                PubCard(
                    mepsLanguageIndex = rs.getInt(1),
                    symbol = rs.getString(2),
                    year = rs.getInt(3),
                    issueTag = rs.getString(4),
                )
            }
        }
    }

    fun readWeeks(dbFile: Path): List<JwPubWeekRow> = withConnection(dbFile) { conn ->
        val list = mutableListOf<JwPubWeekRow>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT DocumentId, MepsDocumentId, Title, Subtitle, Content FROM Document " +
                    "WHERE Class='106' ORDER BY DocumentId",
            ).use { rs ->
                while (rs.next()) {
                    list += JwPubWeekRow(
                        documentId = rs.getInt(1),
                        mepsDocumentId = rs.getLong(2),
                        title = rs.getString(3),
                        subtitle = rs.getString(4),
                        content = rs.getBytes(5),
                    )
                }
            }
        }
        list
    }

    private fun <T> withConnection(dbFile: Path, block: (Connection) -> T): T {
        val url = "jdbc:sqlite:${dbFile.toAbsolutePath()}"
        val conn = DriverManager.getConnection(url)
        try {
            return block(conn)
        } finally {
            conn.close()
        }
    }
}
