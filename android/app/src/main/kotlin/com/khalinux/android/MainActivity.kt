package com.khalinux.android

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.khalinux.runtime.RuntimeEvent

/**
 * Dashboard/terminal mínimo da Fase 3.
 * Toda execução passa pelo KhaLinuxRuntimeService; a Activity apenas apresenta estado e eventos.
 */
class MainActivity : Activity() {
    private var service: KhaLinuxRuntimeService? = null
    private var bound = false
    private var currentSessionId: String? = null
    private lateinit var statusView: TextView
    private lateinit var commandInput: EditText
    private lateinit var outputView: TextView
    private lateinit var runButton: Button
    private lateinit var cancelButton: Button

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as KhaLinuxRuntimeService.LocalBinder).service()
            bound = true
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        bindService(
            Intent(this, KhaLinuxRuntimeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun createContentView(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF101418.toInt())
        }
        statusView = TextView(this).apply { setTextColor(0xFF80CBC4.toInt()) }
        commandInput = EditText(this).apply {
            hint = "comando no KhaLinux"
            setText("printf 'KhaLinux\\n'")
            setSingleLine(true)
            setTextColor(0xFFE8F0F2.toInt())
            setHintTextColor(0xFF8A9AA0.toInt())
        }
        runButton = Button(this).apply {
            text = "Executar"
            setOnClickListener { runCommand() }
        }
        cancelButton = Button(this).apply {
            text = "Cancelar"
            setOnClickListener { currentSessionId?.let { service?.cancel(it) } }
        }
        outputView = TextView(this).apply {
            setTextColor(0xFFE8F0F2.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP
        }
        val outputScroll = ScrollView(this).apply { addView(outputView) }
        root.addView(statusView)
        root.addView(commandInput, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(runButton)
        root.addView(cancelButton)
        root.addView(outputScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        updateStatus()
        return root
    }

    private fun runCommand() {
        val activeService = service ?: return
        outputView.append("\\n$ ${commandInput.text}\\n")
        try {
            currentSessionId = activeService.startShell(commandInput.text.toString(), listener = object : KhaLinuxRuntimeService.EventListener {
                override fun onEvent(event: RuntimeEvent) {
                    runOnUiThread { renderEvent(event) }
                }
            })
            updateStatus()
        } catch (error: IllegalStateException) {
            outputView.append("erro: ${error.message}\\n")
        }
    }

    private fun renderEvent(event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.Stdout -> outputView.append(String(event.bytes, Charsets.UTF_8))
            is RuntimeEvent.Stderr -> outputView.append(String(event.bytes, Charsets.UTF_8))
            is RuntimeEvent.Exited -> {
                outputView.append("\\n[exit=${event.exitCode}]\\n")
                updateStatus()
            }
            is RuntimeEvent.Failed -> outputView.append("\\n[falha=${event.message}]\\n")
            is RuntimeEvent.Started -> updateStatus()
        }
    }

    private fun updateStatus() {
        val activeService = service
        statusView.text = when {
            !bound -> "KhaLinux · conectando ao Service..."
            activeService == null -> "KhaLinux · Service indisponível"
            activeService.isRuntimeInstalled() -> "KhaLinux · runtime instalado · sessão=${currentSessionId ?: "nenhuma"}"
            else -> "KhaLinux · RootFS ainda não instalado"
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
