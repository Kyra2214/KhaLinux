package com.khalinux.runtime.operations

enum class AndroidPackageOrigin { SYSTEM_PARTITION, DATA_PARTITION, APEX, UNKNOWN }

data class AndroidPackageEntry(
    val packageName: String,
    val codePath: String?,
    val installer: String?,
    val uid: Int?,
    val versionCode: Long?,
    val origin: AndroidPackageOrigin
)

/** Parser da saída de `pm list packages -f -i -U --show-versioncode`. */
object AndroidPackageInventoryParser {
    private val packageNamePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*$")
    private val installerPattern = Regex("(?:^|\\s)installer=([^\\s]+)")
    private val uidPattern = Regex("(?:^|\\s)uid[:=]\\s*([0-9]+)")
    private val versionPattern = Regex("(?:^|\\s)versionCode[:=]\\s*([0-9]+)")

    fun parse(output: String): List<AndroidPackageEntry> {
        val packages = linkedMapOf<String, AndroidPackageEntry>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("package:")) return@forEach
            val payload = line.removePrefix("package:").substringBefore(" ").trim()
            if (payload.isBlank()) return@forEach
            val hasPath = '=' in payload
            val codePath = if (hasPath) payload.substringBeforeLast('=').takeIf { it.isNotBlank() } else null
            val packageName = if (hasPath) payload.substringAfterLast('=') else payload
            if (!packageNamePattern.matches(packageName)) return@forEach
            val installer = installerPattern.find(line)?.groupValues?.getOrNull(1)
                ?.takeUnless { it.equals("null", true) || it == "-" }
            val uid = uidPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val versionCode = versionPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val origin = when {
                codePath?.startsWith("/system") == true ||
                    codePath?.startsWith("/product") == true ||
                    codePath?.startsWith("/vendor") == true ||
                    codePath?.startsWith("/odm") == true ||
                    codePath?.startsWith("/system_ext") == true -> AndroidPackageOrigin.SYSTEM_PARTITION
                codePath?.startsWith("/data/app") == true -> AndroidPackageOrigin.DATA_PARTITION
                codePath?.startsWith("/apex") == true -> AndroidPackageOrigin.APEX
                else -> AndroidPackageOrigin.UNKNOWN
            }
            packages[packageName] = AndroidPackageEntry(
                packageName = packageName,
                codePath = codePath,
                installer = installer,
                uid = uid,
                versionCode = versionCode,
                origin = origin
            )
        }
        return packages.values.sortedBy { it.packageName }
    }
}


data class AndroidPermissionRecord(
    val name: String,
    val granted: Boolean?,
    val flags: List<String>,
    val section: String
)

data class AndroidAppOpRecord(
    val operation: String,
    val mode: String,
    val detail: String
)

data class AndroidPackageSecurityReport(
    val packageName: String,
    val permissions: List<AndroidPermissionRecord>,
    val appOps: List<AndroidAppOpRecord>,
    val rawPackageDetails: String,
    val rawAppOps: String
)

/** Interpreta permissões e AppOps sem descartar as saídas originais do Android. */
object AndroidPackageSecurityParser {
    private val permissionPattern = Regex("^(android\\.permission\\.[A-Za-z0-9_.]+)(?::\\s*(.*))?$")
    private val grantedPattern = Regex("(?:^|[,\\s])granted=(true|false)")
    private val flagsPattern = Regex("flags=\\[([^]]*)]")
    private val appOpPattern = Regex("^([A-Z0-9_]+):\\s*([^;\\s]+)(?:;\\s*(.*))?$")

    fun permissions(output: String): List<AndroidPermissionRecord> {
        val result = linkedMapOf<String, AndroidPermissionRecord>()
        var section = "metadata"
        var pending: String? = null
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.equals("requested permissions:", true) -> { section = "requested"; pending = null; return@forEach }
                line.equals("install permissions:", true) -> { section = "install"; pending = null; return@forEach }
                line.equals("runtime permissions:", true) -> { section = "runtime"; pending = null; return@forEach }
                line.endsWith(" permissions:", true) -> { section = line.removeSuffix(":"); pending = null; return@forEach }
            }
            val match = permissionPattern.matchEntire(line)
            if (match != null) {
                val name = match.groupValues[1]
                val detail = match.groupValues.getOrNull(2).orEmpty()
                val granted = grantedPattern.find(detail)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
                val flags = flagsPattern.find(detail)?.groupValues?.getOrNull(1)
                    ?.split('|')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
                val previous = result[name]
                result[name] = AndroidPermissionRecord(
                    name,
                    granted ?: previous?.granted,
                    (previous?.flags.orEmpty() + flags).distinct(),
                    section
                )
                pending = name
            } else if (pending != null && (line.startsWith("granted=") || line.startsWith("flags=") || line.contains("granted="))) {
                val name = pending!!
                val previous = result[name] ?: return@forEach
                val granted = grantedPattern.find(line)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
                val flags = flagsPattern.find(line)?.groupValues?.getOrNull(1)
                    ?.split('|')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
                result[name] = previous.copy(granted = granted ?: previous.granted, flags = (previous.flags + flags).distinct())
            } else if (line.isNotBlank() && !line.startsWith("android.permission.")) {
                pending = null
            }
        }
        return result.values.sortedBy { it.name }
    }

    fun appOps(output: String): List<AndroidAppOpRecord> =
        output.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            val match = appOpPattern.matchEntire(line) ?: return@mapNotNull null
            AndroidAppOpRecord(
                operation = match.groupValues[1],
                mode = match.groupValues[2],
                detail = match.groupValues.getOrNull(3).orEmpty()
            )
        }.toList()
}
