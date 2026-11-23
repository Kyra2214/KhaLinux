package com.khalinux.runtime.operations

private val OPERATION_ID = Regex("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$")
private val FIELD_ID = Regex("^[a-z][a-z0-9_]*$")

enum class LabOperationDomain { DEVICE, NETWORK, WIFI, WEB, FORENSICS }
enum class LabTargetKind { LOCAL_RUNTIME, ANDROID_DEVICE, NETWORK, WEB_SERVICE, APK_FILE, PCAP_FILE }
enum class LabExecutorKind { ROOTFS_ARGV, TOOLKIT, ANDROID_API, ADB, HTTP_SERVICE, PCAP_ANALYZER }
enum class LabParameterType { STRING, INTEGER, BOOLEAN, ENUM }
enum class LabSessionState { ACTIVE, CLOSED, INTERRUPTED }
enum class LabExecutionState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT, INTERRUPTED }
enum class LabEventSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

data class LabOperationParameter(
    val id: String, val type: LabParameterType, val required: Boolean,
    val description: String, val choices: List<String> = emptyList()
) {
    init {
        require(FIELD_ID.matches(id) && description.isNotBlank())
        if (type == LabParameterType.ENUM) require(choices.isNotEmpty() && choices.distinct().size == choices.size)
        else require(choices.isEmpty())
    }
}

/** Contrato declarativo; execução e interpolação segura pertencem ao motor LAB-1. */
data class LabOperationDefinition(
    val id: String, val version: Int, val title: String, val domain: LabOperationDomain,
    val targetKind: LabTargetKind, val executor: LabExecutorKind, val description: String,
    val command: List<String> = emptyList(), val toolId: String? = null,
    val requiredPackages: List<String> = emptyList(), val requiredCapabilities: List<String> = emptyList(),
    val parameters: List<LabOperationParameter> = emptyList(), val outputFields: List<String>,
    val timeoutSeconds: Int = 60, val enabled: Boolean = true
) {
    init {
        require(OPERATION_ID.matches(id) && version > 0)
        require(title.isNotBlank() && title.length <= 100 && description.isNotBlank())
        require(timeoutSeconds in 1..3600)
        require(command.none { it.isBlank() || it.any(Char::isISOControl) })
        require(requiredPackages.distinct().size == requiredPackages.size)
        require(requiredCapabilities.all(String::isNotBlank) && requiredCapabilities.distinct().size == requiredCapabilities.size)
        require(parameters.map { it.id }.distinct().size == parameters.size)
        require(outputFields.isNotEmpty() && outputFields.all(String::isNotBlank) && outputFields.distinct().size == outputFields.size)
        when (executor) {
            LabExecutorKind.ROOTFS_ARGV, LabExecutorKind.ADB -> require(command.isNotEmpty())
            LabExecutorKind.TOOLKIT -> require(!toolId.isNullOrBlank())
            LabExecutorKind.ANDROID_API -> require(requiredCapabilities.isNotEmpty())
            LabExecutorKind.HTTP_SERVICE -> require(targetKind == LabTargetKind.WEB_SERVICE || targetKind == LabTargetKind.LOCAL_RUNTIME)
            LabExecutorKind.PCAP_ANALYZER -> require(targetKind == LabTargetKind.PCAP_FILE)
        }
    }
}

data class LabOperationCatalog(val schemaVersion: Int, val catalogVersion: String, val operations: List<LabOperationDefinition>) {
    init {
        require(schemaVersion == 1 && catalogVersion.isNotBlank() && operations.isNotEmpty())
        require(operations.map { it.id }.distinct().size == operations.size)
    }
    private val index = operations.associateBy { it.id }
    fun find(id: String): LabOperationDefinition? = index[id]
}

data class LabAsset(
    val id: String, val kind: LabTargetKind, val displayName: String, val primaryAddress: String? = null,
    val attributes: Map<String, String> = emptyMap(), val capabilities: Set<String> = emptySet(),
    val source: String, val firstSeenAtEpochMillis: Long, val lastSeenAtEpochMillis: Long
) {
    init { require(id.isNotBlank() && displayName.isNotBlank() && source.isNotBlank()); require(firstSeenAtEpochMillis > 0 && lastSeenAtEpochMillis >= firstSeenAtEpochMillis) }
}

data class LabSession(
    val id: String, val label: String, val assetIds: Set<String>, val state: LabSessionState,
    val createdAtEpochMillis: Long, val closedAtEpochMillis: Long? = null
) {
    init {
        require(id.isNotBlank() && label.isNotBlank() && createdAtEpochMillis > 0)
        require(closedAtEpochMillis == null || closedAtEpochMillis >= createdAtEpochMillis)
        require((state == LabSessionState.ACTIVE) == (closedAtEpochMillis == null))
    }
}

data class LabEvidenceReference(
    val id: String, val executionId: String, val kind: String, val source: String, val relativePath: String,
    val sha256: String? = null, val sizeBytes: Long? = null, val createdAtEpochMillis: Long
) {
    init {
        require(id.isNotBlank() && executionId.isNotBlank() && kind.isNotBlank() && source.isNotBlank())
        require(relativePath.isNotBlank() && !relativePath.startsWith("/"))
        require(sizeBytes == null || sizeBytes >= 0)
        require(createdAtEpochMillis > 0)
        require(sha256 == null || Regex("^[a-fA-F0-9]{64}$").matches(sha256))
    }
}

data class LabExecutionRecord(
    val id: String, val sessionId: String, val operationId: String, val operationVersion: Int,
    val assetIds: Set<String>, val state: LabExecutionState, val queuedAtEpochMillis: Long,
    val startedAtEpochMillis: Long? = null, val endedAtEpochMillis: Long? = null,
    val exitCode: Int? = null, val errorSummary: String? = null, val evidenceIds: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank() && sessionId.isNotBlank() && operationId.isNotBlank() && operationVersion > 0)
        require(queuedAtEpochMillis > 0)
        require(startedAtEpochMillis == null || startedAtEpochMillis >= queuedAtEpochMillis)
        require(endedAtEpochMillis == null || endedAtEpochMillis >= (startedAtEpochMillis ?: queuedAtEpochMillis))
        require(evidenceIds.distinct().size == evidenceIds.size)
    }
}

data class LabEventRecord(
    val id: String, val sessionId: String, val executionId: String?, val assetId: String?,
    val eventType: String, val severity: LabEventSeverity, val occurredAtEpochMillis: Long,
    val source: String, val summary: String, val evidenceIds: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank() && sessionId.isNotBlank() && eventType.isNotBlank())
        require(source.isNotBlank() && summary.isNotBlank() && occurredAtEpochMillis > 0)
        require(evidenceIds.distinct().size == evidenceIds.size)
    }
}
