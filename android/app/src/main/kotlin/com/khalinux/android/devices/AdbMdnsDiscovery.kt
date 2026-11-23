package com.khalinux.android.devices

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class AdbMdnsKind { PAIRING, CONNECT }

data class AdbMdnsEndpoint(
    val id: String,
    val serviceName: String,
    val serviceType: String,
    val host: String,
    val port: Int,
    val kind: AdbMdnsKind
) {
    val endpoint: String
        get() = (if (host.contains(":")) "[$host]" else host) + ":" + port
}

/** Descobre os serviços TLS de Wireless debugging anunciados por mDNS na rede local. */
class AdbMdnsDiscovery(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val listeners = CopyOnWriteArrayList<NsdManager.DiscoveryListener>()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    private var multicastLock: WifiManager.MulticastLock? = null

    @Suppress("DEPRECATION")
    fun start(
        onFound: (AdbMdnsEndpoint) -> Unit,
        onLost: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        check(!closed.get()) { "Descoberta mDNS encerrada" }
        check(started.compareAndSet(false, true)) { "Descoberta mDNS já iniciada" }
        if (multicastLock == null) {
            multicastLock = wifi.createMulticastLock("khalinux-adb-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        listOf(
            "_adb-tls-pairing._tcp." to AdbMdnsKind.PAIRING,
            "_adb-tls-connect._tcp." to AdbMdnsKind.CONNECT
        ).forEach { (type, kind) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains(type.removeSuffix("."))) return
                    val serviceId = serviceInfo.serviceType + "|" + serviceInfo.serviceName
                    if (!resolving.add(serviceId)) return
                    runCatching {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                resolving.remove(serviceId)
                                onError("Falha ao resolver serviço mDNS ${info.serviceName}: código ${errorCode}")
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                resolving.remove(serviceId)
                                val address = info.host?.hostAddress ?: return
                                if (info.port !in 1..65535) return
                                val endpoint = AdbMdnsEndpoint(
                                    id = serviceId,
                                    serviceName = info.serviceName,
                                    serviceType = info.serviceType,
                                    host = address,
                                    port = info.port,
                                    kind = kind
                                )
                                onFound(endpoint)
                            }
                        })
                    }.onFailure {
                        resolving.remove(serviceId)
                        onError(it.message ?: "Falha ao resolver serviço mDNS")
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val serviceId = serviceInfo.serviceType + "|" + serviceInfo.serviceName
                    resolving.remove(serviceId)
                    onLost(serviceId)
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                    onError("Falha ao iniciar descoberta $serviceType: código $errorCode")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                    onError("Falha ao parar descoberta $serviceType: código $errorCode")
                }
            }
            listeners.add(listener)
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listeners.forEach { listener -> runCatching { nsd.stopServiceDiscovery(listener) } }
        listeners.clear()
        resolving.clear()
        multicastLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        multicastLock = null
    }
}
