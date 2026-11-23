package com.khalinux.android.devices

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.khalinux.runtime.operations.LabAsset
import com.khalinux.runtime.operations.LabTargetKind
import java.net.Inet4Address

/** Coleta somente atributos locais de inventário, sem identificadores persistentes de hardware. */
class AndroidDeviceInventory(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): LabAsset {
        val now = System.currentTimeMillis()
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val links = network?.let { connectivity.getLinkProperties(it) }
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        val addresses = links?.linkAddresses.orEmpty().mapNotNull { it.address.hostAddress }
        val ipv4 = links?.linkAddresses.orEmpty().firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        }?.address?.hostAddress
        val ipv6 = links?.linkAddresses.orEmpty().firstOrNull {
            it.address !is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
        }?.address?.hostAddress
        val gateway = links?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val dns = links?.dnsServers.orEmpty().mapNotNull { it.hostAddress }
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val internet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val manager = appContext.packageManager
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Android" }
        val model = Build.MODEL.orEmpty().ifBlank { Build.DEVICE.orEmpty() }
        val displayName = listOf(manufacturer, model).filter { it.isNotBlank() }.distinct().joinToString(" ")

        val attributes = linkedMapOf(
            "manufacturer" to manufacturer,
            "model" to model,
            "brand" to Build.BRAND.orEmpty(),
            "device" to Build.DEVICE.orEmpty(),
            "product" to Build.PRODUCT.orEmpty(),
            "board" to Build.BOARD.orEmpty(),
            "hardware" to Build.HARDWARE.orEmpty(),
            "android_release" to Build.VERSION.RELEASE.orEmpty(),
            "android_sdk" to Build.VERSION.SDK_INT.toString(),
            "security_patch" to if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH.orEmpty() else "",
            "primary_abi" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            "supported_abis" to Build.SUPPORTED_ABIS.joinToString(","),
            "network_interface" to links?.interfaceName.orEmpty(),
            "ip_addresses" to addresses.joinToString(","),
            "ipv4" to ipv4.orEmpty(),
            "ipv6" to ipv6.orEmpty(),
            "gateway" to gateway.orEmpty(),
            "dns_servers" to dns.joinToString(","),
            "wifi_transport" to wifi.toString(),
            "network_has_internet_capability" to internet.toString()
        )
        val deviceCapabilities = buildSet {
            add("android.local-device")
            add("android.build-info")
            add("android.network-state")
            add("android.package-manager")
            if (wifi) add("android.transport.wifi")
            if (internet) add("android.network.internet-capability")
            if (manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) add("android.hardware.camera")
            if (manager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) add("android.hardware.microphone")
        }
        return LabAsset(
            id = "local-android",
            kind = LabTargetKind.ANDROID_DEVICE,
            displayName = displayName,
            primaryAddress = ipv4 ?: ipv6,
            attributes = attributes,
            capabilities = deviceCapabilities,
            source = "android.local-device",
            firstSeenAtEpochMillis = now,
            lastSeenAtEpochMillis = now
        )
    }
}
