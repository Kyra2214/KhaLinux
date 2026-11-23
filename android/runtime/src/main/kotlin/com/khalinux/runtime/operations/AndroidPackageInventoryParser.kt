package com.khalinux.runtime.operations

enum class AndroidPackageOrigin { SYSTEM, USER_DATA, APEX, UNKNOWN }

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
    private val uidPattern = Regex("(?:^|\\s)uid[:=]([0-9]+)")
    private val versionPattern = Regex("(?:^|\\s)versionCode[:=]([0-9]+)")

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
                    codePath?.startsWith("/system_ext") == true -> AndroidPackageOrigin.SYSTEM
                codePath?.startsWith("/data/app") == true -> AndroidPackageOrigin.USER_DATA
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
