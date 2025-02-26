package mobi.meddle.wehe.data.remote
import android.content.Context
import mobi.meddle.wehe.R
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

/**
 * Manager for SSL certificates
 */
@Singleton
class CertificateManager @Inject constructor(
    private val context: Context
) {
    private lateinit var sslSocketFactory: SSLSocketFactory
    private lateinit var trustManager: X509TrustManager

    init {
        setupMainServerCertificate()
    }

    fun getSSLSocketFactory(): SSLSocketFactory = sslSocketFactory

    fun getTrustManager(): X509TrustManager = trustManager

    fun setupMainServerCertificate() {
        try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certificate = context.resources.openRawResource(R.raw.main).use {
                certificateFactory.generateCertificate(it) as X509Certificate
            }

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("main", certificate)
            }

            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val trustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

            val trustManagers = trustManagerFactory.trustManagers
            check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
                "Unexpected trust managers: ${trustManagers.contentToString()}"
            }

            trustManager = trustManagers[0] as X509TrustManager

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustManagers, null)
            }

            sslSocketFactory = sslContext.socketFactory
        } catch (e: Exception) {
            throw IllegalStateException("Error setting up SSL certificate", e)
        }
    }

    fun setupMetadataServerCertificate() {
        // Similar implementation for metadata server certificate
    }
}