package com.khalinux.runtime.operations

enum class AndroidPermissionGrantType { INSTALL, RUNTIME, UNKNOWN }
enum class AndroidPermissionState { GRANTED, DENIED, UNKNOWN }

data class AndroidPackagePermissionEntry(
    val permissionName: String,
    val requested: Boolean,
    val grantType: AndroidPermissionGrantType,
    val state: AndroidPermissionState,
    val flags: Set<String>
)

data class AndroidAppOpEntry(
    val operation: String,
    val mode: String,
    val details: String
)

data class AndroidPackageSecurityReport(
    val packageName: String,
    val permissions: List<AndroidPackagePermissionEntry>,
    val appOps: List<AndroidAppOpEntry>,
    val appOpsAvailable: Boolean,
    val appOpsError: String? = null
)

/** Interpreta dumpsys package e cmd appops sem inferir concessões ausentes. */
object AndroidPackageSecurityParser {
    private enum class Section { NONE, REQUESTED, INSTALL, RUNTIME }
    private data class Grant(
        val type: AndroidPermissionGrantType,
        val granted: Boolean?,
        val flags: Set<String>
    )

    private val permissionName = Regex("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+$")
    private val grantedPattern = Regex("(?:^|[,\\s])granted=(true|false)")
    private val flagsPattern = Regex("flags=\\[([^]]*)]")
    private val appOpPattern = Regex("^([A-Za-z0-9_:]+):\\s*([A-Za-z_]+)(.*)$")

    fun parsePackageDump(packageName: String, output: String): List<AndroidPackagePermissionEntry> {
        val requested = linkedSetOf<String>()
        val grants = linkedMapOf<String, Grant>()
        var section = Section.NONE

        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when (line) {
                "requested permissions:" -> { section = Section.REQUESTED; return@forEach }
                "install permissions:" -> { section = Section.INSTALL; return@forEach }
                "runtime permissions:" -> { section = Section.RUNTIME; return@forEach }
            }
            if (line.isBlank()) return@forEach
            if (line.endsWith(":") && !line.startsWith("android.permission.") &&
                section != Section.NONE && !permissionName.matches(line.removeSuffix(":"))) {
                section = Section.NONE
                return@forEach
            }

            when (section) {
                Section.REQUESTED -> {
                    val name = line.substringBefore(':').trim()
                    if (permissionName.matches(name)) requested.add(name)
                }
                Section.INSTALL, Section.RUNTIME -> {
                    val name = line.substringBefore(':').trim()
                    if (permissionName.matches(name)) {
                        val granted = grantedPattern.find(line)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
                        val flags = flagsPattern.find(line)?.groupValues?.getOrNull(1)
                            ?.split('|', ',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
                        grants[name] = Grant(
                            type = if (section == Section.INSTALL) AndroidPermissionGrantType.INSTALL
                                else AndroidPermissionGrantType.RUNTIME,
                            granted = granted,
                            flags = flags
                        )
                    }
                }
                Section.NONE -> Unit
            }
        }

        return (requested + grants.keys).distinct().sorted().map { name ->
            val grant = grants[name]
            AndroidPackagePermissionEntry(
                permissionName = name,
                requested = name in requested,
                grantType = grant?.type ?: AndroidPermissionGrantType.UNKNOWN,
                state = when (grant?.granted) {
                    true -> AndroidPermissionState.GRANTED
                    false -> AndroidPermissionState.DENIED
                    null -> AndroidPermissionState.UNKNOWN
                },
                flags = grant?.flags.orEmpty()
            )
        }
    }

    fun parseAppOps(output: String): List<AndroidAppOpEntry> =
        output.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            val match = appOpPattern.matchEntire(line) ?: return@mapNotNull null
            val operation = match.groupValues[1]
            if (operation.equals("Uid", true) || operation.equals("No", true)) return@mapNotNull null
            AndroidAppOpEntry(
                operation = operation,
                mode = match.groupValues[2].lowercase(),
                details = match.groupValues[3].trim()
            )
        }.distinctBy { it.operation }.sortedBy { it.operation }

    fun combine(
        packageName: String,
        packageDump: String,
        appOpsOutput: String,
        appOpsError: String? = null
    ): AndroidPackageSecurityReport = AndroidPackageSecurityReport(
        packageName = packageName,
        permissions = parsePackageDump(packageName, packageDump),
        appOps = if (appOpsError == null) parseAppOps(appOpsOutput) else emptyList(),
        appOpsAvailable = appOpsError == null,
        appOpsError = appOpsError
    )
}
