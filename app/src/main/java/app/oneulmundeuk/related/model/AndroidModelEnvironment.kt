package app.oneulmundeuk.related.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import java.io.File

/** App-private, never backed up / transferred (also excluded in data_extraction_rules), apart from Room / DataStore. */
fun modelRoot(context: Context): File = File(context.noBackupFilesDir, "models")

fun freeBytesAt(dir: File): Long = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)

/**
 * Wi-Fi / unmetered via the standard ConnectivityManager. Needs ACCESS_NETWORK_STATE, which is not declared yet
 * (nothing can be downloaded until a model host exists) → SecurityException → false: a download never starts on an
 * unknown network. TODO(model-host): declare INTERNET + ACCESS_NETWORK_STATE together with the first real source.
 */
class AndroidNetworkCheck(private val context: Context) : NetworkCheck {
    override fun isUnmetered(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)
}
