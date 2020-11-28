/*
 * Copyright (C) 2020 Charlotte Skye.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.lotte.tinlok.net.tls.x509

import external.openssl.*
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import tf.lotte.tinlok.Unsafe
import tf.lotte.tinlok.net.tls.x509.X509Certificate.Companion.fromPEM
import tf.lotte.tinlok.system.toKStringUtf8Fast
import tf.lotte.tinlok.util.Closeable

// https://zakird.com/2013/10/13/certificate-parsing-with-openssl

/**
 * A public-key certificate that is in the X.509 format. This is the certificate format used for
 * public TLS certificates, for example. A certificate contains certain standard attribute, which
 * are exposed via various properties, as well as some non-standard extensions, some of which are
 * exposed via properties too.
 *
 * Creating new instances of this class directly is not allowed; use the helper methods on the
 * companion object to parse and load certificates. A certificate can be loaded from a PEM-encoded
 * string with [fromPEM].
 */
public actual class X509Certificate private actual constructor() : Closeable {
    private constructor(handle: CPointer<X509>) : this() {
        this.handle = handle
    }

    public actual companion object {
        /**
         * Creates an [X509Certificate] from a PEM-encoded certificate.
         */
        @Unsafe
        public actual fun fromPEM(pem: String): X509Certificate = memScoped {
            val bio = BIO_new(BIO_s_mem())
            defer { BIO_free(bio) }
            val pemStr = pem.cstr
            BIO_write(bio, pemStr, pemStr.size)
            val x509 = PEM_read_bio_X509(bio, null, null, null)
                ?: error("Failed to create X509 certificate")

            return X509Certificate(x509)
        }

        /**
         * Gets the [X509Certificate] from an [SSL] struct.
         */
        public fun fromSSL(ssl: CPointer<SSL>): X509Certificate {
            val cert = SSL_get_peer_certificate(ssl) ?: error("SSL object has no peer certificate")
            return X509Certificate(cert)
        }
    }

    // Note: This is a lateinit because I don't think there's a way to have the constructor differ
    // in the common sourceset.
    /** Underlying handle to the OpenSSL X509 struct. */
    private lateinit var handle: CPointer<X509>

    override fun close() {
        if (::handle.isInitialized) X509_free(handle)
    }

    /**
     * The X.509 version of this certificate. See 4.1.2.1.
     */
    public actual val version: Long get () {
        return X509_get_version(handle)
    }

    /**
     * The serial number of this certificate. This can be any arbitrary number, so this is a String
     * (at least for now).
     */
    @OptIn(Unsafe::class)
    public actual val serial: String get() = memScoped {
        // Ew, yuck, gross!
        val i = X509_get_serialNumber(handle) ?: error("Failed to get serial number?")
        val bn = ASN1_INTEGER_to_BN(i, null) ?: error("Failed to convert serial to bignum")
        defer { BN_free(bn) }
        // temporary char array returned
        val tmp = BN_bn2dec(bn) ?: error("Failed to convert bignum to decimal")
        defer { K_OPENSSL_free(tmp) }
        return tmp.toKStringUtf8Fast()
    }


    /** The entity that has signed and issued this certificate. */
    @OptIn(ExperimentalUnsignedTypes::class, Unsafe::class)
    public actual val issuer: List<Pair<String, String>> get() {
        // an X509_name has multiple entries, which we iterate over and return a "mapping" of
        val name = X509_get_issuer_name(handle) ?: error("Failed to get issuer name")
        return name.toPairs()
    }

    /** The entity this certificate was issued for. */
    @OptIn(Unsafe::class)
    public actual val subject: List<Pair<String, String>> get() {
        // similar deal here.
        val name = X509_get_subject_name(handle) ?: error("Failed to get subject name")
        return name.toPairs()
    }

    /**
     * The signature algorithm used for this certificate's signature. This is named (confusingly)
     * just ``signature`` in RFC 5280.
     */
    public actual val signatureAlgorithm: String get() = TODO()
}