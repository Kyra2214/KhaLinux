package com.khalinux.android.toolkit

import com.khalinux.runtime.packages.PackageCatalog
import com.khalinux.runtime.toolkit.ToolkitRegistry
import com.khalinux.runtime.toolkit.ToolkitRegistryParser
import org.json.JSONArray
import org.json.JSONObject

/** Adaptador org.json → estruturas simples consumidas por [ToolkitRegistryParser]. */
object ToolkitJson {
    fun parse(text: String, catalog: PackageCatalog): ToolkitRegistry =
        ToolkitRegistryParser.parse(toMap(JSONObject(text)), catalog)

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
