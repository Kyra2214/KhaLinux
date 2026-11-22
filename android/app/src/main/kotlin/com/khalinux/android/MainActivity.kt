package com.khalinux.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.khalinux.android.data.ServiceTerminalRuntimeGateway

/** Activity de hospedagem da UI Compose; a execução continua encapsulada no Service. */
class MainActivity : ComponentActivity() {
    private val viewModel: TerminalViewModel by viewModels()
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? KhaLinuxRuntimeService.LocalBinder
            if (localBinder == null) {
                viewModel.bindingUnavailable()
                return
            }
            viewModel.attachRuntime(ServiceTerminalRuntimeGateway(localBinder.service()))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The binding remains registered; Android may reconnect this ServiceConnection.
            viewModel.serviceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KhaLinuxApp(viewModel) }
        bound = bindService(
            Intent(this, KhaLinuxRuntimeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) viewModel.bindingUnavailable()
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
