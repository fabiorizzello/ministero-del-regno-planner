package org.example.project.feature.schemas.infrastructure.jwpub

import java.security.MessageDigest

data class PubCard(
    val mepsLanguageIndex: Int,
    val symbol: String,
    val year: Int,
    val issueTag: String,
) {
    fun toPubCardString(): String = "${mepsLanguageIndex}_${symbol}_${year}_${issueTag}"
}

data class KeyIv(val key: ByteArray, val iv: ByteArray)

class JwPubContentDecryptor {

    fun deriveKeyIv(pubCard: PubCard): KeyIv {
        val cardBytes = pubCard.toPubCardString().toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(cardBytes)
        val combined = ByteArray(hash.size)
        for (i in hash.indices) combined[i] = (hash[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        return KeyIv(key = combined.copyOfRange(0, 16), iv = combined.copyOfRange(16, 32))
    }

    companion object {
        // Public constant from sws2apps/meeting-schedules-parser (MIT).
        private val XOR_KEY: ByteArray = hexToBytes(
            "11cbb5587e32846d4c26790c633da289f66fe5842a3a585ce1bc3a294af5ada7",
        )

        private fun hexToBytes(hex: String): ByteArray {
            val out = ByteArray(hex.length / 2)
            for (i in out.indices) {
                out[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
            return out
        }
    }
}
