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

class StartInteractiveTerminalUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(rows: Int, columns: Int, observer: RuntimeEventObserver): String {
        require(rows in 1..200 && columns in 1..400) { "Dimensões de terminal inválidas" }
        return gateway.startInteractive(rows, columns, observer)
    }
}

class WriteTerminalInputUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(sessionId: String, text: String): Boolean {
        require(sessionId.isNotBlank()) { "O identificador da sessão não pode estar vazio" }
        require(text.isNotEmpty()) { "A entrada do terminal não pode estar vazia" }
        return gateway.writeInput(sessionId, text)
    }
}

class ResizeTerminalUseCase(private val gateway: TerminalRuntimeGateway) {
    operator fun invoke(sessionId: String, rows: Int, columns: Int): Boolean {
        require(sessionId.isNotBlank()) { "O identificador da sessão não pode estar vazio" }
        require(rows in 1..65535 && columns in 1..65535) { "Dimensões de terminal inválidas" }
        return gateway.resize(sessionId, rows, columns)
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
    val startInteractive = StartInteractiveTerminalUseCase(gateway)
    val writeInput = WriteTerminalInputUseCase(gateway)
    val resize = ResizeTerminalUseCase(gateway)
    val cancelSession = CancelTerminalSessionUseCase(gateway)
}
