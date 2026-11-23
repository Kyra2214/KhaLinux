package com.khalinux.android.red

import org.json.JSONObject

/** Parser estrito do catálogo embarcado. Rejeita ids duplicados e operações incompletas. */
object RedCatalogJson {
    fun parse(text: String): List<RedOperation> {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Versão de catálogo RED não suportada." }
        val array = root.getJSONArray("operations")
        val operations = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val stepsJson = item.getJSONArray("steps")
            val steps = (0 until stepsJson.length()).map { stepIndex ->
                val step = stepsJson.getJSONObject(stepIndex)
                RedTutorialStep(step.getString("id"), step.getString("title"), step.getString("instructions"))
            }
            require(steps.isNotEmpty()) { "Operação sem etapas." }
            require(steps.map { it.id }.distinct().size == steps.size) { "Etapas duplicadas." }
            RedOperation(
                id = item.getString("id"),
                number = item.getInt("number"),
                title = item.getString("title"),
                category = RedCategory.valueOf(item.getString("category")),
                difficulty = RedDifficulty.valueOf(item.getString("difficulty")),
                summary = item.getString("summary"),
                objectives = strings(item.getJSONArray("objectives")),
                prerequisites = strings(item.getJSONArray("prerequisites")),
                steps = steps,
                completionCriteria = strings(item.getJSONArray("completionCriteria")),
                environment = item.getString("environment")
            )
        }
        require(operations.isNotEmpty()) { "Catálogo RED vazio." }
        require(operations.map { it.id }.distinct().size == operations.size) { "Ids de operação duplicados." }
        require(operations.map { it.number }.distinct().size == operations.size) { "Números de operação duplicados." }
        require(operations.all { it.id.isNotBlank() && it.title.isNotBlank() && it.summary.isNotBlank() })
        return operations
    }

    private fun strings(array: org.json.JSONArray): List<String> =
        (0 until array.length()).map { array.getString(it) }
}
