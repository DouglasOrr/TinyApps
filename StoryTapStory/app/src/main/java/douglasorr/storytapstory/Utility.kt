package douglasorr.storytapstory

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
fun currentTimeISO(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

fun logNonFatal(tag: String, message: String, e: Exception) {
    Log.e(tag, "$message, error: $e")
}

fun assertNotOnMainThread(message: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        throw RuntimeException(message)
    }
}
