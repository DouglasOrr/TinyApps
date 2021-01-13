package douglasorr.storytapstory

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Looper
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat")
fun currentTimeISO(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

fun logNonFatal(tag: String, message: String, e: Throwable) {
    Log.e(tag, "$message, error: $e")
}

fun assertNotOnMainThread(message: String) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        throw RuntimeException(message)
    }
}

/**
 * Helper for managing MediaPlayer playback.
 */
class Player {
    private val subject = BehaviorSubject.create<Event>()
    private val player = MediaPlayer().apply {
        setOnPreparedListener {
            subject.onNext(Event.Start)
        }
        setOnCompletionListener {
            subject.onNext(Event.End)
        }
    }

    sealed class Event {
        object Start : Event()
        object End : Event()
    }

    // Getters

    fun isPlaying() = player.isPlaying

    fun updates(): Observable<Event> = subject.observeOn(AndroidSchedulers.mainThread())

    // Actions

    fun play(track: File) {
        stop()
        player.apply {
            setDataSource(track.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        player.apply {
            stop()
            reset()
        }
    }

    fun release() {
        player.release()
    }
}
