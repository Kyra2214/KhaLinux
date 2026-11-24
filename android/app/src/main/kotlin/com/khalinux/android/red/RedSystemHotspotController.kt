package com.khalinux.android.red

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Integra o hotspot Wi-Fi gerenciado pelo Android sem usar APIs ocultas.
 *
 * O operador liga o hotspot nas Configurações do sistema. O KhaLinux identifica
 * somente um IPv4 novo e inequívoco, hospeda o mesmo portal HTTPS e observa se
 * o aparelho A possui upstream validado. Ativar dados móveis depois pode
 * disponibilizar internet a B sem trocar a rede Wi-Fi, conforme suporte da OEM/operadora.
 */
class RedSystemHotspotController(
    context: Context,
    private val onUpstreamChanged: (RedUpstreamSnapshot) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var baseline: Set<Pair<String, String>> = emptySet()
    @Volatile private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastUpstream: RedUpstreamSnapshot? = null

    fun openSystemHotspotSettings(): Boolean {
        val initial = snapshotWifiIpv4Addresses() ?: return false
        baseline = initial
        startUpstreamMonitoring()
        val primary = Intent("android.settings.TETHER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            if (primary.resolveActivity(appContext.packageManager) != null) {
                appContext.startActivity(primary)
            } else {
                appContext.startActivity(fallback)
            }
            true
        }.getOrDefault(false)
    }

    fun discoverSystemHotspot(): RedHotspotSnapshot {
        startUpstreamMonitoring()
        val current = snapshotWifiIpv4Addresses()
            ?: return RedHotspotSnapshot(
                RedNetworkState.ERROR,
                "Não foi possível enumerar as interfaces Wi-Fi para identificar o hotspot do sistema."
            )
        val newlyObserved = current - baseline
        val defaultAddresses = connectivity.activeNetwork
            ?.let { network -> connectivity.getLinkProperties(network)?.linkAddresses }
            .orEmpty()
            .mapNotNull { it.address.hostAddress }
            .toSet()
        val nonDefault = current.filter { (_, address) -> address !in defaultAddresses }.toSet()
        val apNamed = current.filter { (name, _) ->
            val normalized = name.lowercase(Locale.ROOT)
            normalized.startsWith("ap") || normalized.startsWith("swlan") || normalized.contains("softap")
        }.toSet()
        val candidates = when {
            apNamed.size == 1 -> apNamed
            newlyObserved.size == 1 -> newlyObserved
            nonDefault.size == 1 -> nonDefault
            newlyObserved.isNotEmpty() -> newlyObserved
            else -> nonDefault
        }
        return when {
            candidates.size == 1 -> {
                val (interfaceName, address) = candidates.single()
                RedHotspotSnapshot(
                    state = RedNetworkState.HOTSPOT_READY,
                    message = "Hotspot do sistema identificado por um IPv4 novo. SSID e senha permanecem sob controle do Android.",
                    interfaceName = interfaceName,
                    ipv4Address = address
                )
            }
            candidates.size > 1 -> RedHotspotSnapshot(
                state = RedNetworkState.ERROR,
                message = "Mais de uma interface/IPv4 novo foi observado. O vínculo é ambíguo e o portal não será iniciado."
            )
            else -> RedHotspotSnapshot(
                state = RedNetworkState.DISCOVERING_INTERFACE,
                message = "Hotspot do sistema ainda não identificado. Ative o hotspot nas Configurações do Android e tente detectar novamente."
            )
        }
    }

    private fun startUpstreamMonitoring() {
        if (callback != null) return
        lastUpstream = null
        val observer = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                report(connectivity.getNetworkCapabilities(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                report(networkCapabilities)
            }

            override fun onLost(network: Network) {
                publishUpstream(
                    RedUpstreamSnapshot(
                        internetAvailable = false,
                        validated = false,
                        transport = null,
                        message = "O aparelho A perdeu a rede upstream observada."
                    )
                )
            }
        }
        try {
            connectivity.registerDefaultNetworkCallback(observer)
            callback = observer
        } catch (_: Exception) {
            publishUpstream(
                RedUpstreamSnapshot(
                    internetAvailable = false,
                    validated = false,
                    transport = null,
                    message = "Não foi possível observar o upstream do aparelho A por callback público."
                )
            )
        }
    }

    private fun report(capabilities: NetworkCapabilities?) {
        if (capabilities == null) return
        val internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "celular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "não identificado"
        }
        val message = when {
            validated -> "Aparelho A possui upstream de internet validado ($transport). O encaminhamento até B depende do hotspot do sistema e da operadora."
            internet -> "Aparelho A detectou rede com capacidade INTERNET, mas sem validação de acesso externo ($transport)."
            else -> "Aparelho A está sem upstream de internet; o portal local continua independente."
        }
        publishUpstream(RedUpstreamSnapshot(internet, validated, transport, message))
    }

    fun stopMonitoring() {
        val active = callback
        if (active != null) {
            runCatching { connectivity.unregisterNetworkCallback(active) }
            callback = null
            publishUpstream(
                RedUpstreamSnapshot(
                    internetAvailable = false,
                    validated = false,
                    transport = null,
                    message = "Monitoramento de upstream encerrado; hotspot do sistema não foi desligado pelo KhaLinux."
                )
            )
        }
    }

    private fun publishUpstream(snapshot: RedUpstreamSnapshot) {
        if (lastUpstream == snapshot) return
        lastUpstream = snapshot
        onUpstreamChanged(snapshot)
    }

    private fun snapshotWifiIpv4Addresses(): Set<Pair<String, String>>? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { network ->
                val name = network.name.lowercase(Locale.ROOT)
                network.isUp && !network.isLoopback &&
                    (name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("swlan") || name.contains("softap") || name.contains("wifi"))
            }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .mapNotNull { address -> address.hostAddress?.let { network.name to it } }
            }
            .toSet()
    }.getOrNull()
}
