package com.khalinux.android.domain

class IsRuntimeInstalledUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(): Boolean = gateway.isInstalled()
}

class StartTerminalCommandUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(command: String, observer: RuntimeEventObserver): String {
        require(command.isNotBlank()) { "O comando não pode estar vazio" }
        return gateway.start(command, observer)
    }
}

class CancelTerminalSessionUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(sessionId: String): Boolean {
        require(sessionId.isNotBlank()) { "O identificador da sessão não pode estar vazio" }
        return gateway.cancel(sessionId)
    }
}

class TerminalUseCases(gateway: TerminalRuntimeGateway) {
    val isRuntimeInstalled = IsRuntimeInstalledUseCase(gateway)
    val startCommand = StartTerminalCommandUseCase(gateway)
    val cancelSession = CancelTerminalSessionUseCase(gateway)
}
