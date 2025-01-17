/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.net.tls

import external.openssl.*
import external.openssl.certs.CA_BUNDLE
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.convert
import tf.veriny.tinlok.Unsafe
import tf.veriny.tinlok.net.tls.x509.X509Certificate
import tf.veriny.tinlok.net.tls.x509.X509CertificateChain
import tf.veriny.tinlok.util.*

/**
 * OWASP Cipher List A. The default list.
 */
private const val CIPHER_LIST_A = "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:" +
    "TLS_AES_128_GCM_SHA256:DHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:" +
    "ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256"

/**
 * OWASP Cipher List B. Used if compatibility mode is absolutely desired.
 */
private const val CIPHER_LIST_B = "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:" +
    "TLS_AES_128_GCM_SHA256:DHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:" +
    "ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256:DHE-RSA-AES256-SHA256:" +
    "DHE-RSA-AES128-SHA256:ECDHE-RSA-AES256-SHA384:ECDHE-RSA-AES128-SHA256"

/**
 * A [TlsContext] wraps an OpenSSL SSL_CTX, and produces new [TlsObject] instances with the
 * default config.
 */
@Unsafe
public actual class TlsContext actual constructor(
    public actual val config: TlsContextConfiguration,
) : Closeable {
    private val scope = ClosingScopeImpl()

    /** Boolean to prevent double-frees. */
    private val isOpen = AtomicBoolean(true)

    /**
     * The OpenSSL context held by this class.
     */
    private val ctx = run {
        when (config) {
            is TlsClientConfig -> SSL_CTX_new(TLS_client_method())
            is TlsServerConfig -> SSL_CTX_new(TLS_server_method())
        } ?: tlsError()
    }

    init {
        // Set the default, unconfigurable properties.
        // 1) Disable SSL re-negotiation. This adds extra complexity and isn't part of TLS 1.3.
        SSL_CTX_set_options(ctx, SSL_OP_NO_RENEGOTIATION.convert())
        // 2) Disable tickets. These are broken, see: https://github.com/openssl/openssl/issues/7948
        tlsError { SSL_CTX_set_num_tickets(ctx, 0) }
        // 3) Disable compression. This is a security hole.
        SSL_CTX_set_options(ctx, SSL_OP_NO_COMPRESSION.convert())
        // 4a) Enable certification verification, if we're on the client side.
        if (config is TlsClientConfig) {
            // This enables server-side verification.
            val bits = flags(SSL_VERIFY_PEER, SSL_VERIFY_FAIL_IF_NO_PEER_CERT)
            SSL_CTX_set_verify(ctx, bits, null)

            val store = SSL_CTX_get_cert_store(ctx)
                ?: error("Failed to get context's certificate store")

            // TODO: Allow customising CA locations.
            val chain = X509CertificateChain.fromPEM(CA_BUNDLE)
            scope.add(chain)
            for (cert in chain) {
                tlsError { X509_STORE_add_cert(store, cert.handle) }
            }

            // load any custom certificates
            for (cert in config.extraCertificates) {
                val x509Cert = X509Certificate.fromPEM(cert)
                scope.add(x509Cert)
                tlsError { X509_STORE_add_cert(store, x509Cert.handle) }
            }
        }
        // 4b) Load server-side certificates.
        else if (config is TlsServerConfig) {
            TODO("TLS servers are currently unsupported. Sorry!")

            val cert = X509Certificate.fromPEM(config.certificatePem)
            scope.add(cert)
            tlsError { SSL_CTX_use_certificate(ctx, cert.handle) }
            tlsError { SSL_CTX_use_privatekey_pem(ctx, config.privateKeyPem) }

            // TODO: Extra chain certificates
        }
        // 5) Enable release_buffers. Python does this for newer OpenSSL versions
        // (which we require), and apparently it provides decent memory savings.
        K_SSL_CTX_set_mode(ctx, SSL_MODE_RELEASE_BUFFERS.convert())

        // 6) Set a good set of secure ciphers.
        if (config.compatibilityCiphers) {
            tlsError { SSL_CTX_set_cipher_list(ctx, CIPHER_LIST_B) }
        } else {
            tlsError { SSL_CTX_set_cipher_list(ctx, CIPHER_LIST_A) }
        }

        // 7) Set the application properties, which are configurable.
        val sorted = TlsVersion.values().sorted().map { it.number }

        // Minimum protocol version supported, usually TLS 1.2
        tlsError { K_SSL_CTX_set_min_proto_version(ctx, sorted.minOrNull()!!.convert()) }

        // Max supported, usually TLS 1.3
        tlsError { K_SSL_CTX_set_max_proto_version(ctx, sorted.maxOrNull()!!.convert()) }

        // TODO: ALPN. This requires a callback...
    }

    override fun close() {
        if (!isOpen.compareAndSet(expected = true, new = false)) return
        SSL_CTX_free(ctx)
        scope.close()
    }

    /**
     * Creates a new [TlsObject] from this client context.
     * (This will fail if this is a server context).
     */
    public actual fun clientObject(hostname: String): TlsObject {
        return TlsObject(this, hostname)
    }

    /**
     * Creates a new [TlsObject] from this server context.
     * (This will fail if this is a client context).
     */
    public actual fun serverObject(): TlsObject {
        return TlsObject(this, "")
    }

    /**
     * Creates a new SSL object from this context.
     */
    internal fun SSL_new(): CPointer<SSL> {
        return SSL_new(ctx) ?: error("Failed to create new SSL object")
    }
}
