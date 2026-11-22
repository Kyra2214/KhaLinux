package com.khalinux.android.labs

import com.khalinux.runtime.labs.LabCatalog
import com.khalinux.runtime.labs.LabCatalogParser
import org.json.JSONArray
import org.json.JSONObject

/** Adaptador org.json → estruturas simples consumidas por [LabCatalogParser]. */
object LabsJson {
    fun parse(text: String): LabCatalog = LabCatalogParser.parse(toMap(JSONObject(text)))

    private fun toMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { toValue(obj.get(it)) }

    private fun toList(array: JSONArray): List<Any?> = (0 until array.length()).map { toValue(array.get(it)) }

    private fun toValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> toMap(value)
        is JSONArray -> toList(value)
        else -> value
    }
}
