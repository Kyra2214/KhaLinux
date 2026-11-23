package com.khalinux.android.operations

import com.khalinux.runtime.operations.*
import org.json.JSONArray
import org.json.JSONObject

/** Parser estrito do catálogo operacional. Não executa comandos nem cria resultados. */
object OperationCatalogJson {
    fun parse(text: String): LabOperationCatalog {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Versão de catálogo operacional não suportada." }
        val array = root.getJSONArray("operations")
        val operations = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val parameters = item.optJSONArray("parameters")?.let { values ->
                (0 until values.length()).map { parameterIndex ->
                    val p = values.getJSONObject(parameterIndex)
                    LabOperationParameter(
                        p.getString("id"), LabParameterType.valueOf(p.getString("type")),
                        p.getBoolean("required"), p.getString("description"), strings(p.optJSONArray("choices"))
                    )
                }
            } ?: emptyList()
            LabOperationDefinition(
                id = item.getString("id"), version = item.getInt("version"),
                title = item.getString("title"), domain = LabOperationDomain.valueOf(item.getString("domain")),
                targetKind = LabTargetKind.valueOf(item.getString("targetKind")),
                executor = LabExecutorKind.valueOf(item.getString("executor")),
                description = item.getString("description"), command = strings(item.optJSONArray("command")),
                toolId = optionalString(item, "toolId"),
                requiredPackages = strings(item.optJSONArray("requiredPackages")),
                requiredCapabilities = strings(item.optJSONArray("requiredCapabilities")),
                parameters = parameters, outputFields = strings(item.getJSONArray("outputFields")),
                timeoutSeconds = item.optInt("timeoutSeconds", 60),
                enabled = if (item.has("enabled")) item.getBoolean("enabled") else true
            )
        }
        return LabOperationCatalog(root.getInt("schemaVersion"), root.getString("catalogVersion"), operations)
    }

    private fun strings(array: JSONArray?): List<String> =
        array?.let { values -> (0 until values.length()).map { values.getString(it) } } ?: emptyList()

    private fun optionalString(item: JSONObject, key: String): String? =
        if (!item.has(key) || item.isNull(key)) null else item.getString(key)
}
