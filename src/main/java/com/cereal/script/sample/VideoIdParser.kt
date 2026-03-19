package com.cereal.script.sample

import java.net.URI

object VideoIdParser {

    // YouTube video IDs are always exactly 11 characters
    private val RAW_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return tryParseAsUrl(trimmed) ?: tryParseAsRawId(trimmed)
    }

    private fun tryParseAsUrl(input: String): String? {
        return try {
            val uri = URI(input)
            when {
                // https://youtu.be/<id>
                (uri.host == "youtu.be" || uri.host?.endsWith(".youtu.be") == true) -> {
                    uri.path?.removePrefix("/")?.substringBefore("/")?.takeIf { RAW_ID_REGEX.matches(it) }
                }
                // https://www.youtube.com/watch?v=<id>
                uri.host?.contains("youtube.com") == true -> {
                    uri.query
                        ?.split("&")
                        ?.firstOrNull { it.startsWith("v=") }
                        ?.removePrefix("v=")
                        ?.takeIf { RAW_ID_REGEX.matches(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryParseAsRawId(input: String): String? =
        if (RAW_ID_REGEX.matches(input)) input else null
}
