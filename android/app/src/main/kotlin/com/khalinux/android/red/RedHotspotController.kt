package com.khalinux.android.red

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Controla somente o LocalOnlyHotspot fornecido pelo Android.
 * SSID/senha são mostrados apenas quando a API os disponibiliza.
 */
class RedHotspotController(
    context: Context,
    private val onState: (RedHotspotSnapshot) -> Unit
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile private var starting = false

    fun start() {
        if (reservation != null || starting) return
        starting = true
        onState(RedHotspotSnapshot(RedNetworkState.REQUESTING_HOTSPOT, "Solicitando hotspot local ao Android."))
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    starting = false
                    reservation = value
                    val config = value.wifiConfiguration
                    val network = discoverWifiInterface()
                    onState(
                        RedHotspotSnapshot(
                            state = RedNetworkState.HOTSPOT_READY,
                            message = "Hotspot local ativado pelo Android.",
                            ssid = config?.SSID?.trim('"'),
                            passphrase = config?.preSharedKey,
                            interfaceName = network?.first,
                            ipv4Address = network?.second
                        )
                    )
                }

                override fun onStopped() {
                    starting = false
                    reservation = null
                    onState(RedHotspotSnapshot(RedNetworkState.IDLE, "O Android encerrou o hotspot local."))
                }

                override fun onFailed(reason: Int) {
                    starting = false
                    reservation = null
                    onState(
                        RedHotspotSnapshot(
                            state = RedNetworkState.ERROR,
                            message = "O Android não conseguiu iniciar o hotspot local.",
                            androidFailureCode = reason
                        )
                    )
                }
            }, Handler(Looper.getMainLooper()))
        } catch (security: SecurityException) {
            starting = false
            onState(
                RedHotspotSnapshot(
                    state = RedNetworkState.ERROR,
                    message = "O Android bloqueou a solicitação. Verifique as permissões de Wi-Fi/localização e os serviços do sistema."
                )
            )
        } catch (error: Exception) {
            starting = false
            onState(
                RedHotspotSnapshot(
                    state = RedNetworkState.ERROR,
                    message = "Falha ao solicitar hotspot local: ${error.message ?: "erro desconhecido"}"
                )
            )
        }
    }

    fun stop() {
        starting = false
        val active = reservation
        reservation = null
        runCatching { active?.close() }
        onState(RedHotspotSnapshot(RedNetworkState.IDLE, "Hotspot local liberado pelo operador."))
    }

    private fun discoverWifiInterface(): Pair<String, String>? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val candidates = interfaces.filter { network ->
            val name = network.name.lowercase(Locale.ROOT)
            network.isUp && !network.isLoopback &&
                (name.startsWith("wlan") || name.startsWith("ap") ||
                    name.startsWith("swlan") || name.contains("softap"))
        }
        candidates.asSequence()
            .flatMap { network ->
                network.inetAddresses.toList().asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { network.name to it.hostAddress }
            }
            .firstOrNull { !it.second.isNullOrBlank() }
            ?.let { it.first to it.second!! }
    }.getOrNull()
}
