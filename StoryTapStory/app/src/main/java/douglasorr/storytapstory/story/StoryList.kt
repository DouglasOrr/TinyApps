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

        fun rename(oldName: String, newName: String) = update { data ->
            val oldFile = path(oldName)
            val newFile = path(newName)
            Log.d(TAG, "rename() ${newFile.exists()} ${newFile.isDirectory} ${data.stories.contains(newName)}")
            if (!oldFile.isDirectory || !data.stories.contains(oldName)) {
                Log.w(TAG, "Warning: rename() cannot find \"$oldName\" in $directory")
                return@update null
            }
            // Would like to check newFile.exists() here, but it is case-insensitive, which is annoying
            if (data.stories.contains(newName)) {
                Log.w(TAG, "Warning: rename() target \"$newName\" already exists in $directory")
                return@update null
            }
            if (!oldFile.renameTo(newFile)) {
                Log.w(TAG, "Warning: rename() failed \"$oldName\" -> \"$newName\" in $directory")
                return@update null
            }
            return@update Data(data.stories.filterNot { it == oldName }.plus(newName))
        }

        fun delete(name: String) = update { data ->
            val file = path(name)
            if (!file.isDirectory || !data.stories.contains(name)) {
                Log.w(TAG, "Warning: delete() cannot find \"$name\" in $directory")
                return@update null
            }
            if (!file.deleteRecursively()) {
                Log.w(TAG, "Warning: delete() failed for \"$name\" in $directory")
                return@update null
            }
            return@update Data(data.stories.filterNot { it == name })
        }

        var data = doRefresh().apply { subject.onNext(this) }

        private fun update(op: (Data) -> Data?) {
            assertNotOnMainThread("StoryList update")
            op(this.data)?.let {
                subject.onNext(it)
                this.data = it
            }
        }

        private fun doRefresh(): Data {
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

    fun rename(oldName: String, newName: String) {
        scheduler.scheduleDirect {
            worker!!.rename(oldName, newName)
        }
    }

    fun delete(name: String) {
        scheduler.scheduleDirect {
            worker!!.delete(name)
        }
    }
}