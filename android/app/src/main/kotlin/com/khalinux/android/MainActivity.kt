package com.khalinux.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.khalinux.android.data.ServiceTerminalRuntimeGateway
import com.khalinux.android.packages.PackagesViewModel
import com.khalinux.android.labs.LabsViewModel
import com.khalinux.android.toolkit.ToolkitViewModel
import com.khalinux.android.files.FilesViewModel
import com.khalinux.android.tutor.TutorViewModel
import com.khalinux.android.red.RedViewModel
import com.khalinux.android.blue.BlueViewModel

/** Activity de hospedagem da UI Compose; a execução continua encapsulada no Service. */
class MainActivity : ComponentActivity() {
    private val viewModel: TerminalViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val packagesViewModel: PackagesViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val toolkitViewModel: ToolkitViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val labsViewModel: LabsViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val filesViewModel: FilesViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val redViewModel: RedViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val blueViewModel: BlueViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private val tutorViewModel: TutorViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private var bound = false

    // A instalação roda mesmo sem a permissão; ela só controla a visibilidade da notificação de progresso.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? KhaLinuxRuntimeService.LocalBinder
            if (localBinder == null) {
                viewModel.bindingUnavailable()
                return
            }
            val gateway = ServiceTerminalRuntimeGateway(localBinder.service())
            viewModel.attachRuntime(gateway)
            packagesViewModel.attach(gateway)
            toolkitViewModel.attach(gateway)
            labsViewModel.attach(gateway)
            filesViewModel.attach(gateway)
            tutorViewModel.attach(gateway)
            redViewModel.attachRuntime(gateway)
            blueViewModel.attachRuntime(gateway)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The binding remains registered; Android may reconnect this ServiceConnection.
            viewModel.serviceDisconnected()
            redViewModel.detachRuntime()
            blueViewModel.detachRuntime()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.onTrimMemory(level)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Com targetSdk 28 o edge-to-edge não é automático; o Compose (systemBars/ime insets) depende dele.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { KhaLinuxApp(viewModel, packagesViewModel, toolkitViewModel, labsViewModel, filesViewModel, tutorViewModel, redViewModel, blueViewModel) }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        bound = bindService(
            Intent(this, KhaLinuxRuntimeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) {
            viewModel.bindingUnavailable()
            redViewModel.detachRuntime()
            blueViewModel.detachRuntime()
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
