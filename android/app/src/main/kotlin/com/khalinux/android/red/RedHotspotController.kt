package com.khalinux.android.red

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

/** Usa o hotspot local do Android; não configura nem clona redes de terceiros. */
class RedHotspotController(context: Context, private val onState: (String, String?) -> Unit) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Volatile private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start() {
        if (reservation != null) return
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = value
                    val config = value.wifiConfiguration
                    onState("Hotspot local ativado pelo Android.", "SSID: ${config?.SSID ?: "fornecido pelo sistema"}\nSenha: ${config?.preSharedKey ?: "consulte as configurações do sistema"}")
                }
                override fun onStopped() {
                    reservation = null
                    onState("O Android encerrou o hotspot local.", null)
                }
                override fun onFailed(reason: Int) {
                    reservation = null
                    onState("Não foi possível iniciar o hotspot local (código Android $reason). Ative um hotspot manualmente nas configurações e tente novamente.", null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (security: SecurityException) {
            onState("Permissão de localização/Wi-Fi necessária ou serviço de localização indisponível. Conceda a permissão no próprio aparelho e tente novamente.", null)
        } catch (error: Exception) {
            onState("Falha ao solicitar hotspot local: ${error.message ?: "erro desconhecido"}", null)
        }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
        onState("Hotspot local liberado.", null)
    }
}
