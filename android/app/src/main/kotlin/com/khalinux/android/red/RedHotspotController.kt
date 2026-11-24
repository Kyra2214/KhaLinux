package com.khalinux.android.red

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Controla somente o LocalOnlyHotspot fornecido pelo Android.
 * Cada solicitação tem geração própria; callbacks atrasados não reativam sessões canceladas.
 * A interface AP é aceita somente quando um endereço IPv4 novo é observado após a solicitação.
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
    private var interfaceDiscoveryRunnable: Runnable? = null
    private var interfacesBeforeStart: Set<Pair<String, String>> = emptySet()

    @Synchronized
    fun start() {
        if (reservation != null || starting) return

        requestGeneration += 1
        val generation = requestGeneration
        activeRequestGeneration = generation
        starting = true
        val baseline = snapshotWifiIpv4Addresses()
        if (baseline == null) {
            starting = false
            activeRequestGeneration = null
            requestGeneration += 1
            onState(RedHotspotSnapshot(RedNetworkState.ERROR, "Não foi possível obter a lista inicial de interfaces; hotspot não solicitado."))
            return
        }
        interfacesBeforeStart = baseline
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
                        onState(
                            RedHotspotSnapshot(
                                state = RedNetworkState.DISCOVERING_INTERFACE,
                                message = "Hotspot criado pelo Android; identificando a interface e o IPv4 desta sessão."
                            )
                        )
                        scheduleInterfaceDiscovery(generation, value, interfacesBeforeStart, DISCOVERY_ATTEMPTS)
                    }
                }

                override fun onStopped() {
                    synchronized(this@RedHotspotController) {
                        if (reservationGeneration != generation) return
                        interfaceDiscoveryRunnable?.let(handler::removeCallbacks)
                        interfaceDiscoveryRunnable = null
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

    private fun scheduleInterfaceDiscovery(
        generation: Long,
        activeReservation: WifiManager.LocalOnlyHotspotReservation,
        baseline: Set<Pair<String, String>>,
        attemptsLeft: Int
    ) {
        val task = Runnable {
            synchronized(this@RedHotspotController) {
                if (reservationGeneration != generation || reservation !== activeReservation) return@synchronized
                val candidates = snapshotWifiIpv4Addresses()?.filter { it !in baseline }.orEmpty()
                when {
                    candidates.size == 1 -> {
                        interfaceDiscoveryRunnable = null
                        val (interfaceName, address) = candidates.single()
                        val config = readHotspotDetails(activeReservation)
                        onState(
                            RedHotspotSnapshot(
                                state = RedNetworkState.HOTSPOT_READY,
                                message = "Hotspot local e interface AP identificados por endereço IPv4 novo.",
                                ssid = config.ssid,
                                passphrase = config.passphrase,
                                security = config.security,
                                band = config.band,
                                interfaceName = interfaceName,
                                ipv4Address = address
                            )
                        )
                    }
                    attemptsLeft > 1 -> {
                        scheduleInterfaceDiscovery(generation, activeReservation, baseline, attemptsLeft - 1)
                    }
                    else -> {
                        interfaceDiscoveryRunnable = null
                        reservation = null
                        reservationGeneration = null
                        runCatching { activeReservation.close() }
                        onState(
                            RedHotspotSnapshot(
                                state = RedNetworkState.ERROR,
                                message = if (candidates.isEmpty()) {
                                    "Hotspot criado, mas não foi possível correlacionar uma interface Wi-Fi e um IPv4 novos. O portal não foi iniciado."
                                } else {
                                    "Mais de uma interface/IPv4 candidata foi observada; vínculo ambíguo. O portal não foi iniciado."
                                }
                            )
                        )
                    }
                }
            }
        }
        interfaceDiscoveryRunnable = task
        handler.postDelayed(task, INTERFACE_POLL_INTERVAL_MS)
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
        interfaceDiscoveryRunnable?.let(handler::removeCallbacks)
        interfaceDiscoveryRunnable = null

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

    private data class HotspotDetails(
        val ssid: String?,
        val passphrase: String?,
        val security: String?,
        val band: String?
    )

    @Suppress("DEPRECATION")
    private fun readHotspotDetails(reservation: WifiManager.LocalOnlyHotspotReservation): HotspotDetails {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val config = reservation.softApConfiguration
            HotspotDetails(
                ssid = config.ssid,
                passphrase = config.passphrase,
                security = null,
                band = null
            )
        } else {
            val config = reservation.wifiConfiguration
            HotspotDetails(
                ssid = config?.SSID?.trim('"'),
                passphrase = config?.preSharedKey,
                security = null,
                band = null
            )
        }
    }

    private fun snapshotWifiIpv4Addresses(): Set<Pair<String, String>>? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { network ->
                val name = network.name.lowercase(Locale.ROOT)
                network.isUp && !network.isLoopback &&
                    (name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("swlan") || name.contains("softap"))
            }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .mapNotNull { address -> address.hostAddress?.let { network.name to it } }
            }
            .toSet()
    }.getOrNull()

    companion object {
        private const val START_TIMEOUT_MS = 30_000L
        private const val INTERFACE_POLL_INTERVAL_MS = 500L
        private const val DISCOVERY_ATTEMPTS = 20
    }
}
