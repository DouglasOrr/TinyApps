package douglasorr.storytapstory.story

import android.util.Log
import douglasorr.storytapstory.assertNotOnMainThread
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.io.File

private const val TAG = "StoryList"

/**
 * Handles a directory containing story directories.
 */
class StoryList(val directory: File) {

    data class Data(val stories: List<String>)

    inner class Worker {
        fun create(name: String) = update { data ->
            val file = path(name)
            if (file.isDirectory || data.stories.contains(name)) {
                Log.w(TAG, "Warning: create() target \"$name\" already exists in $directory")
                return@update null
            }
            if (!file.mkdirs()) {
                Log.w(TAG, "Warning: create() failed for \"$name\" in $directory")
                return@update null
            }
            return@update Data(data.stories.plus(name))
        }

        fun refresh() = update { data ->
            val newData = reload()
            if (data == newData) null else newData
        }

        var data = reload().apply { subject.onNext(this) }

        private fun update(op: (Data) -> Data?) {
            assertNotOnMainThread("StoryList update")
            op(this.data)?.let {
                subject.onNext(it)
                this.data = it
            }
        }

        private fun reload(): Data {
            assertNotOnMainThread("StoryList refresh")
            // Use null-asserting dereference as the directory definitely should exist by this point
            val stories = directory.listFiles()!!.filter { it.isDirectory }.map { it.name }
            return Data(stories)
        }
    }

    private val scheduler = Schedulers.single()
    private val subject = BehaviorSubject.create<Data>()
    private var worker: Worker? = null
    init {
        directory.mkdirs()
        scheduler.scheduleDirect {
            worker = Worker()
        }
    }

    // Getters

    fun updates(): Observable<Data> = subject.observeOn(AndroidSchedulers.mainThread())

    fun path(name: String) = File(directory, name)

    // Actions

    fun create(name: String) {
        scheduler.scheduleDirect {
            worker!!.create(name)
        }
    }

    fun refresh() {
        scheduler.scheduleDirect {
            worker!!.refresh()
        }
    }
}
