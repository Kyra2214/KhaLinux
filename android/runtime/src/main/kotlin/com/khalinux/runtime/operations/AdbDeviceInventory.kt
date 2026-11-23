package com.khalinux.runtime.operations

import java.security.MessageDigest

data class AdbDeviceEntry(
    val serial: String,
    val state: String,
    val descriptors: Map<String, String>
)

/** Converte a saída real de adb devices/getprop em ativos persistíveis. */
object AdbDeviceInventory {
    private val propertyLine = Regex("^\\[([^\\]]+)]\\s*:\\s*\\[(.*)]$")

    fun parseDevices(output: String): List<AdbDeviceEntry> =
        output.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
            .mapNotNull { line ->
                val fields = line.split(Regex("\\s+"))
                if (fields.size < 2) return@mapNotNull null
                val descriptors = fields.drop(2).mapNotNull { token ->
                    val separator = token.indexOf(':')
                    if (separator <= 0) null else token.substring(0, separator) to token.substring(separator + 1)
                }.toMap()
                AdbDeviceEntry(fields[0], fields[1], descriptors)
            }.toList()

    fun assetId(serial: String): String {
        require(serial.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256").digest(serial.toByteArray(Charsets.UTF_8))
        val shortHash = digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "adb-$shortHash"
    }

    fun toAsset(
        entry: AdbDeviceEntry,
        propertiesOutput: String = "",
        previous: LabAsset? = null,
        now: Long = System.currentTimeMillis()
    ): LabAsset {
        val properties = parseProperties(propertiesOutput)
        val model = properties["ro.product.model"] ?: entry.descriptors["model"] ?: entry.serial
        val manufacturer = properties["ro.product.manufacturer"] ?: ""
        val displayName = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
        val host = hostFromSerial(entry.serial)
        val attributes = linkedMapOf(
            "adb_serial" to entry.serial,
            "adb_state" to entry.state,
            "adb_product" to (entry.descriptors["product"] ?: ""),
            "adb_model_descriptor" to (entry.descriptors["model"] ?: ""),
            "manufacturer" to manufacturer,
            "model" to model,
            "device" to (properties["ro.product.device"] ?: entry.descriptors["device"] ?: ""),
            "product" to (properties["ro.product.name"] ?: entry.descriptors["product"] ?: ""),
            "android_release" to (properties["ro.build.version.release"] ?: ""),
            "android_sdk" to (properties["ro.build.version.sdk"] ?: ""),
            "primary_abi" to (properties["ro.product.cpu.abi"] ?: ""),
            "security_patch" to (properties["ro.build.version.security_patch"] ?: ""),
            "hardware" to (properties["ro.hardware"] ?: ""),
            "build_fingerprint" to (properties["ro.build.fingerprint"] ?: "")
        )
        val capabilities = buildSet {
            add("android.remote-device")
            add("adb.discovered")
            if (entry.state == "device") {
                add("adb.connected")
                if (properties.isNotEmpty()) add("adb.shell")
            }
        }
        return LabAsset(
            id = assetId(entry.serial),
            kind = LabTargetKind.ANDROID_DEVICE,
            displayName = displayName.ifBlank { entry.serial },
            primaryAddress = host,
            attributes = attributes,
            capabilities = capabilities,
            source = "adb.devices",
            firstSeenAtEpochMillis = previous?.firstSeenAtEpochMillis ?: now,
            lastSeenAtEpochMillis = now
        )
    }

    private fun parseProperties(output: String): Map<String, String> =
        output.lineSequence().mapNotNull { line ->
            val match = propertyLine.matchEntire(line.trim()) ?: return@mapNotNull null
            match.groupValues[1] to match.groupValues[2]
        }.toMap()

    private fun hostFromSerial(serial: String): String? {
        if (!serial.contains(':')) return null
        val raw = serial.substringBeforeLast(':')
        return if (raw.startsWith("[") && raw.endsWith("]")) raw.substring(1, raw.length - 1) else raw
    }
}
