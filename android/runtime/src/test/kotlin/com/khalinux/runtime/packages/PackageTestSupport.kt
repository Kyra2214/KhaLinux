package com.khalinux.runtime.packages

import java.time.Duration

internal fun apt(
    name: String,
    version: String = "1.0",
    deps: List<PackageDependency> = emptyList(),
    category: PackageCategory = PackageCategory.NETWORK,
    tags: List<String> = emptyList(),
    validation: PackageValidation = PackageValidation.UNVERIFIED,
    limitations: List<String> = emptyList()
) = PackageManifest(
    schema = 1, name = name, version = version, title = name.uppercase(), description = "Pacote $name para teste",
    category = category, architecture = PackageArchitecture.ARM64,
    source = PackageSource(PackageSourceType.DEBIAN_APT, null, name),
    downloadBytes = null, installedBytes = null,
    verification = PackageVerification(null, null), validation = validation,
    dependencies = deps, limitations = limitations, tags = tags
)

internal class FakeRunner(private val exitCode: Int = 0) : PackageCommandRunner {
    val commands = mutableListOf<String>()
    override fun run(shellCommand: String, timeout: Duration): PackageCommandOutcome {
        commands += shellCommand
        return PackageCommandOutcome(exitCode, if (exitCode == 0) "ok" else "erro simulado")
    }
}
