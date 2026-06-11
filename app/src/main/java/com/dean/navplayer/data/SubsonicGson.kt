package com.dean.navplayer.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/** Subsonic often returns a single object where the schema says an array. */
class SingleOrArrayAdapter<T>(private val clazz: Class<T>) : JsonDeserializer<List<T>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<T> {
        if (json.isJsonNull) return emptyList()
        if (json.isJsonArray) {
            return json.asJsonArray.map { element -> context.deserialize(element, clazz) }
        }
        return listOf(context.deserialize(json, clazz))
    }
}

fun GsonBuilder.withSubsonicAdapters(): GsonBuilder = this
    .registerTypeAdapter(listType(SongJson::class.java), SingleOrArrayAdapter(SongJson::class.java))
    .registerTypeAdapter(listType(PlaylistJson::class.java), SingleOrArrayAdapter(PlaylistJson::class.java))
    .registerTypeAdapter(listType(PlaylistEntryJson::class.java), SingleOrArrayAdapter(PlaylistEntryJson::class.java))

private fun listType(clazz: Class<*>): Type =
    TypeToken.getParameterized(List::class.java, clazz).type
