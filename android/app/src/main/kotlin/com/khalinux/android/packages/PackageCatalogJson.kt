package com.khalinux.android.packages

import com.khalinux.runtime.packages.PackageCatalog
import com.khalinux.runtime.packages.PackageCatalogParser
import org.json.JSONArray
import org.json.JSONObject

/** Adaptador org.json → estruturas simples consumidas por [PackageCatalogParser]. */
object PackageCatalogJson {
    fun parse(text: String): PackageCatalog = PackageCatalogParser.parse(toMap(JSONObject(text)))

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
