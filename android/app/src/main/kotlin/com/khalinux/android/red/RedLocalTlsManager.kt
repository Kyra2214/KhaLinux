package com.khalinux.android.red

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager

/** Identidade TLS do laboratório. Chaves privadas permanecem no Android Keystore. */
class RedLocalTlsManager(context: Context) {
    @Suppress("unused")
    private val applicationContext = context.applicationContext
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    data class Material(
        val sslContext: SSLContext,
        val caCertificateDer: ByteArray,
        val caFingerprintSha256: String,
        val serverFingerprintSha256: String,
        val serverSubjectAlternativeIp: String
    )

    @Synchronized
    fun prepare(ipv4Address: String): Material {
        val address = InetAddress.getByName(ipv4Address)
        require(address is Inet4Address && address.hostAddress == ipv4Address && !address.isAnyLocalAddress) {
            "O certificado TLS exige o IPv4 literal atual do hotspot."
        }
        val caPair = getOrCreateKeyPair(CA_ALIAS, 3072)
        val caCertificate = getOrCreateCaCertificate(caPair)
        val serverPair = replaceKeyPair(SERVER_ALIAS, 2048)
        val serverCertificate = issueServerCertificate(serverPair, caPair, caCertificate, ipv4Address)
        keyStore.setKeyEntry(SERVER_ALIAS, serverPair.private, null, arrayOf(serverCertificate, caCertificate))
        val serverKey = keyStore.getKey(SERVER_ALIAS, null) as? PrivateKey
            ?: error("A chave privada TLS não está disponível no Android Keystore.")
        val keyManager = LabServerKeyManager(serverKey, arrayOf(serverCertificate, caCertificate))
        val sslContext = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), null, SecureRandom()) }
        return Material(
            sslContext, caCertificate.encoded, fingerprint(caCertificate.encoded),
            fingerprint(serverCertificate.encoded), ipv4Address
        )
    }

    private fun getOrCreateCaCertificate(pair: KeyPair): X509Certificate {
        val existing = keyStore.getCertificate(CA_ALIAS) as? X509Certificate
        if (existing != null && existing.basicConstraints >= 0 && existing.notAfter.after(Date())) return existing
        val subject = X500Name("CN=KhaLinux RED Lab CA,O=KhaLinux,C=BR")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject, serialNumber(), Date(now - CLOCK_SKEW_MS), Date(now + CA_VALIDITY_MS), subject, pair.public
        )
        val extensions = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature))
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(pair.public))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(pair.public))
        val certificate = buildCertificate(builder, pair.private)
        certificate.verify(pair.public)
        keyStore.setKeyEntry(CA_ALIAS, pair.private, null, arrayOf(certificate))
        return certificate
    }

    private fun issueServerCertificate(
        serverPair: KeyPair, caPair: KeyPair, caCertificate: X509Certificate, ipv4Address: String
    ): X509Certificate {
        val issuer = X500Name(caCertificate.subjectX500Principal.name)
        val subject = X500Name("CN=$ipv4Address,O=KhaLinux RED Lab")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            issuer, serialNumber(), Date(now - CLOCK_SKEW_MS), Date(now + SERVER_VALIDITY_MS), subject, serverPair.public
        )
        val extensions = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
        builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
        builder.addExtension(Extension.subjectAlternativeName, true, GeneralNames(GeneralName(GeneralName.iPAddress, ipv4Address)))
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(serverPair.public))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(caPair.public))
        return buildCertificate(builder, caPair.private).also { certificate ->
            certificate.verify(caCertificate.publicKey)
            require(certificate.subjectAlternativeNames.orEmpty().any { it.size >= 2 && it[0] == 7 && it[1] == ipv4Address }) {
                "O certificado emitido não contém o SAN IP esperado."
            }
        }
    }

    private fun buildCertificate(
        builder: JcaX509v3CertificateBuilder, signerKey: PrivateKey
    ): X509Certificate {
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signerKey)
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(builder.build(signer)).also { it.checkValidity(Date()) }
    }

    private fun getOrCreateKeyPair(alias: String, bits: Int): KeyPair {
        val certificate = keyStore.getCertificate(alias)
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
        if (certificate != null && privateKey != null) return KeyPair(certificate.publicKey, privateKey)
        return replaceKeyPair(alias, bits)
    }

    private fun replaceKeyPair(alias: String, bits: Int): KeyPair {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(bits).setDigests(KeyProperties.DIGEST_SHA256).build()
        )
        return generator.generateKeyPair()
    }

    private fun serialNumber(): BigInteger = BigInteger(159, SecureRandom()).setBit(158)
    private fun fingerprint(encoded: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

    private class LabServerKeyManager(
        private val privateKey: PrivateKey, private val chain: Array<X509Certificate>
    ) : X509ExtendedKeyManager() {
        override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? =
            if (keyType.equals("RSA", true)) SERVER_ALIAS else null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == SERVER_ALIAS) chain else null
        override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == SERVER_ALIAS) privateKey else null
        override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? =
            if (keyType.equals("RSA", true)) arrayOf(SERVER_ALIAS) else null
    }

    companion object {
        private const val CA_ALIAS = "khalinux_red_lab_ca"
        private const val SERVER_ALIAS = "khalinux_red_lab_server"
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
        private const val CA_VALIDITY_MS = 2L * 365 * 24 * 60 * 60 * 1000
        private const val SERVER_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000
    }
}
