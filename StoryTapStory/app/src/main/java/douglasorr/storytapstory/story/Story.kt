package douglasorr.storytapstory.story

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import douglasorr.storytapstory.assertNotOnMainThread
import douglasorr.storytapstory.currentTimeISO
import douglasorr.storytapstory.logNonFatal
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.io.File
import java.io.IOException


private const val TAG = "Story"
private const val AUDIO_EXTENSION = "3gp"
private const val NEW_RECORDING = "_new_recording"


class Playlist(val directory: File, private val observer: Observer<Data>) {
    private var data: Data = loadOrCreate(directory).apply {
        observer.onNext(this)
    }

    init {
        refresh()
    }

    private fun update(newData: Data) {
        data = newData
        observer.onNext(newData)
    }

    fun refresh() {
        Helper.refresh(data, directory)?.let { update(it) }
    }

    fun move(name: String, position: Int) {
        Helper.move(data, directory, name, position)?.let { update(it) }
    }

    data class Data(val created: String, val updated: String, val tracks: List<String>)

    private companion object Helper {
        private fun fileName(directory: File) = File(directory, "story.json")

        private fun load(file: File) = file.bufferedReader().use {
            Gson().fromJson(it, Data::class.java)
        }

        private fun save(data: Data, file: File): Data {
            file.bufferedWriter().use {
                Gson().toJson(data, it)
            }
            return data
        }

        /**
         * Load an existing playlist from the given file, or create a new (empty) one.
         */
        fun loadOrCreate(directory: File): Data {
            assertNotOnMainThread("Playlist loadOrCreate")
            val file = fileName(directory)
            if (file.isFile) {
                try {
                    return load(file)
                } catch (e: IOException) {
                    logNonFatal(TAG, "cannot read playlist file $file", e)
                } catch (e: JsonParseException) {
                    logNonFatal(TAG, "cannot read JSON from playlist file $file", e)
                }
                Log.d(TAG, "re-creating playlist file $file")
            }
            val now = currentTimeISO()
            return save(Data(now, now, listOf()), file)
        }

        private fun saveUpdated(data: Data, directory: File, tracks: List<String>): Data {
            return save(Data(data.created, currentTimeISO(), tracks), fileName(directory))
        }

        /**
         * Scan the directory for tracks, adding them to the end of the list & saving it.
         */
        fun refresh(data: Data, directory: File): Data? {
            assertNotOnMainThread("Playlist refresh")
            val pattern = Regex("^(.+)\\.$AUDIO_EXTENSION\$")
            // Use null-asserting dereference as the directory definitely should exist by this point
            val foundTracks = (directory.listFiles()
                !!.sortedBy { it.lastModified() }
                .mapNotNull { pattern.find(it.name)?.groupValues?.get(1) }
                .filterNot { it == NEW_RECORDING })

            // Compare against the old to see if we need to update (and preserve existing order)
            val existing = data.tracks.filter { foundTracks.contains(it) }
            val added = foundTracks.filter { !data.tracks.contains(it) }
            if (existing.size == data.tracks.size && added.isEmpty()) {
                Log.d(TAG, "Playlist refresh with no changes")
                return null
            }
            Log.d(TAG, "Playlist refresh removed ${existing.size - data.tracks.size}" +
                    ", added ${added.size}")
            return saveUpdated(data, directory, existing + added)
        }

        /**
         * Try to move a named track in the playlist.
         */
        fun move(data: Data, directory: File, name: String, position: Int): Data? {
            assertNotOnMainThread("Playlist move")
            if (!(0 <= position && position < data.tracks.size)) {
                Log.w(TAG, "Warning: move() destination index out of bounds, " +
                        "$position should be in range [0, ${data.tracks.size}) in playlist $directory")
                return null
            }
            val originalPosition = data.tracks.indexOf(name)
            if (originalPosition == -1) {
                Log.w(TAG, "Warning: move() could not find track \"${name}\" in playlist $directory")
                return null
            }
            if (position == originalPosition) {
                Log.d(TAG, "move() track \"${name}\" already at correct position $position in playlist $directory")
                return null
            }
            // Sometimes (local) mutability is just easier...
            val mutTracks = data.tracks.toMutableList()
            mutTracks.add(position + (if (originalPosition < position) 1 else 0), name)
            mutTracks.removeAt(originalPosition + (if (position < originalPosition) 1 else 0))
            return saveUpdated(data, directory, mutTracks.toList())
        }
    }
}

/**
 * Handles a set of recordings & playlist that correspond to a single story.
 */
class Story(val directory: File) {
    // We use a single threaded scheduler to make sure that disk access is serialized - only this
    // thread is allowed to write files (except _new_recording.3gp, which is written by the
    // MediaRecorder)
    private val scheduler = Schedulers.single()
    private val subject = BehaviorSubject.create<Playlist.Data>()
    private var playlist: Playlist? = null
    init {
        directory.mkdirs()
        scheduler.scheduleDirect {
            playlist = Playlist(directory, subject)
        }
    }

    fun updates(): Observable<Playlist.Data> = subject.observeOn(AndroidSchedulers.mainThread())

    fun move(name: String, position: Int) {
        scheduler.scheduleDirect { playlist!!.move(name, position) }
    }

    fun fileNamed(name: String) = File(directory, "$name.$AUDIO_EXTENSION")

    fun wipRecording() = fileNamed(NEW_RECORDING)

    fun saveRecorded(name: String) {
        scheduler.scheduleDirect {
            val wip = wipRecording()
            if (wip.isFile) {
                wip.renameTo(fileNamed(name))
                playlist?.refresh()
            }
        }
    }

    fun deleteRecorded() {
        scheduler.scheduleDirect {
            val wip = wipRecording()
            if (wip.isFile) {
                wip.delete()
            }
        }
    }
}
