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
 * Cada solicitação tem geração própria: callbacks atrasados nunca podem reativar uma sessão cancelada.
 */
class RedHotspotController(
    context: Context,
    private val onState: (RedHotspotSnapshot) -> Unit
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile private var starting = false
    private var requestGeneration = 0L
    private var activeRequestGeneration: Long? = null
    private var reservationGeneration: Long? = null
    private var timeoutRunnable: Runnable? = null

    @Synchronized
    fun start() {
        if (reservation != null || starting) return

        requestGeneration += 1
        val generation = requestGeneration
        activeRequestGeneration = generation
        starting = true
        onState(RedHotspotSnapshot(RedNetworkState.REQUESTING_HOTSPOT, "Solicitando hotspot local ao Android."))

        val timeout = Runnable {
            synchronized(this@RedHotspotController) {
                if (activeRequestGeneration != generation || !starting) return@synchronized
                starting = false
                activeRequestGeneration = null
                requestGeneration += 1
                timeoutRunnable = null
                onState(
                    RedHotspotSnapshot(
                        state = RedNetworkState.ERROR,
                        message = "O Android não respondeu à solicitação de hotspot dentro do tempo limite."
                    )
                )
            }
        }
        timeoutRunnable = timeout
        handler.postDelayed(timeout, START_TIMEOUT_MS)

        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    synchronized(this@RedHotspotController) {
                        if (activeRequestGeneration != generation || !starting) {
                            runCatching { value.close() }
                            return
                        }
                        timeoutRunnable?.let(handler::removeCallbacks)
                        timeoutRunnable = null
                        starting = false
                        activeRequestGeneration = null
                        reservation = value
                        reservationGeneration = generation

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
                }

                override fun onStopped() {
                    synchronized(this@RedHotspotController) {
                        if (reservationGeneration != generation) return
                        reservation = null
                        reservationGeneration = null
                        onState(RedHotspotSnapshot(RedNetworkState.IDLE, "O Android encerrou o hotspot local."))
                    }
                }

                override fun onFailed(reason: Int) {
                    synchronized(this@RedHotspotController) {
                        if (activeRequestGeneration != generation || !starting) return
                        timeoutRunnable?.let(handler::removeCallbacks)
                        timeoutRunnable = null
                        starting = false
                        activeRequestGeneration = null
                        onState(
                            RedHotspotSnapshot(
                                state = RedNetworkState.ERROR,
                                message = "O Android não conseguiu iniciar o hotspot local.",
                                androidFailureCode = reason
                            )
                        )
                    }
                }
            }, handler)
        } catch (security: SecurityException) {
            failCurrentRequest(
                generation,
                "O Android bloqueou a solicitação. Verifique as permissões de Wi-Fi/localização e os serviços do sistema."
            )
        } catch (error: Exception) {
            failCurrentRequest(
                generation,
                "Falha ao solicitar hotspot local: ${error.message ?: "erro desconhecido"}"
            )
        }
    }

    @Synchronized
    private fun failCurrentRequest(generation: Long, message: String) {
        if (activeRequestGeneration != generation) return
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        starting = false
        activeRequestGeneration = null
        requestGeneration += 1
        onState(RedHotspotSnapshot(RedNetworkState.ERROR, message))
    }

    @Synchronized
    fun stop() {
        val active = reservation
        val wasStarting = starting
        if (active == null && !wasStarting) return

        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null

        if (wasStarting) {
            starting = false
            activeRequestGeneration = null
            requestGeneration += 1
            onState(RedHotspotSnapshot(RedNetworkState.IDLE, "Solicitação de hotspot cancelada pelo operador."))
        }

        if (active != null) {
            reservation = null
            reservationGeneration = null
            requestGeneration += 1
            runCatching { active.close() }
            onState(RedHotspotSnapshot(RedNetworkState.IDLE, "Hotspot local encerrado pelo operador."))
        }
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

    companion object {
        private const val START_TIMEOUT_MS = 30_000L
    }
}
