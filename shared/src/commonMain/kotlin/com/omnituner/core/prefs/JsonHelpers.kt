package com.omnituner.core.prefs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

val PREFS_JSON = Json { ignoreUnknownKeys = true }

fun parseJsonOrNull(raw: String): JsonElement? = try {
    PREFS_JSON.parseToJsonElement(raw)
} catch (_: Exception) {
    null
}

fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

fun JsonObject?.field(key: String): JsonElement? = this?.get(key)

fun JsonElement?.asString(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

fun String.hexColorOrNull(): String? =
    if (Regex("^#[0-9a-fA-F]{6}$").matches(this)) lowercase() else null
