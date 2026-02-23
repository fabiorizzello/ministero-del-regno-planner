package org.example.project.core.util

import org.slf4j.LoggerFactory

@PublishedApi
internal val enumParsingLogger = LoggerFactory.getLogger("EnumParsing")

inline fun <reified T : Enum<T>> enumByName(name: String, default: T): T {
    return try {
        enumValueOf<T>(name)
    } catch (_: IllegalArgumentException) {
        enumParsingLogger.warn(
            "Valore enum sconosciuto '{}' per {}; uso default '{}'",
            name, T::class.simpleName, default.name,
        )
        default
    }
}
