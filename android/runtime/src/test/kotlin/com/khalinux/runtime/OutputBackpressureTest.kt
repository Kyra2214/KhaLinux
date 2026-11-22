package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputBackpressureTest {
    private val rootfs = Files.createTempDirectory("khalinux-bp").toFile().apply {
        File(this, "bin/bash").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n") }
    }
    private val proot = File(rootfs, "runtime/proot").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true) }

    @After fun cleanup() { rootfs.deleteRecursively() }

    @Test(timeout = 60000) fun slowConsumerStillReceivesAllBytesAndExit() {
        val launcher = ProotLauncher(
            ProotConfig(prootExecutable = proot, rootfsDirectory = rootfs),
            processLauncher = object : ProcessLauncher {
                override fun launch(arguments: List<String>, environment: Map<String, String>): Process =
                    ProcessBuilder("/bin/bash", "-c", "head -c 6000000 /dev/zero").start()
            }
        )
        SessionManager(launcher).use { runtime ->
            val session = runtime.start(CommandRequest("ignored"))
            var bytes = 0L
            var exited = false
            var chunks = 0
            for (event in session.events()) {
                when (event) {
                    is RuntimeEvent.Stdout -> {
                        bytes += event.bytes.size
                        if (++chunks <= 600) Thread.sleep(2) // consumidor lento: a fila limitada é exercitada
                    }
                    is RuntimeEvent.Exited -> exited = true
                    else -> Unit
                }
            }
            assertEquals(6_000_000L, bytes)
            assertTrue(exited)
        }
    }
}
