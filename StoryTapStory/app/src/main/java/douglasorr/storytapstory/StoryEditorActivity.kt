package douglasorr.storytapstory

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.*
import douglasorr.storytapstory.story.Story
import java.io.File

private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 200
private const val TAG = "StoryEditorActivity"

class StoryEditorActivity : BaseActivity() {
    private val recorder = Recorder()
    private val player = Player()
    private var story: Story? = null

    class Recorder {
        private val recorder = MediaRecorder()

        fun start(recording: File) {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                setOutputFile(recording.absolutePath)
                prepare()
                start()
            }
        }

        fun stop(): Boolean {
            return try {
                recorder.stop()
                true
            } catch (e: RuntimeException) {
                logNonFatal(TAG, "probably called stop() too soon", e)
                false
            } finally {
                recorder.reset()
            }
        }

        fun release() {
            recorder.release()
        }
    }

    //region ListAdapter

    object TrackDiffer : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    inner class TrackDragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP + ItemTouchHelper.DOWN, 0) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val src = viewHolder as TrackAdapter.ViewHolder
            src.name?.let { story!!.moveTrack(it, target.adapterPosition) }
            return true  // assume it will happen, asynchronously
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            throw AssertionError("We don't support swiping")
        }
    }

    inner class TrackAdapter : ListAdapter<String, TrackAdapter.ViewHolder>(TrackDiffer) {
        inner class ViewHolder(root: ViewGroup): RecyclerView.ViewHolder(root) {
            var name: String? = null
            val title: TextView = root.findViewById(R.id.track_title)

            init {
                root.findViewById<View>(R.id.track_play).setOnClickListener {
                    name?.let { play(it) }
                }
                root.findViewById<View>(R.id.track_delete).setOnClickListener {
                    name?.let { askUserToDeleteTrack(it) }
                }
            }

            fun bind(name: String) {
                this.name = name
                title.text = name
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(inflater.inflate(R.layout.list_item_track, parent, false) as ViewGroup)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    //endregion

    //region Actions

    private fun play(name: String) {
        player.play(story!!.trackNamed(name))
    }

    private fun askUserToDeleteTrack(name: String) {
        AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.delete_dialog_title, name))
            setPositiveButton(R.string.label_yes) { _, _ ->
                story!!.deleteTrack(name)
            }
            setNegativeButton(R.string.label_no) { _, _ -> }
        }.create().show()
    }

    private fun askUserToSave() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.save_dialog_title)
            val root = layoutInflater.inflate(R.layout.dialog_save, null)
            setView(root)
            setPositiveButton(R.string.label_save) { _, _ ->
                val name = root.findViewById<EditText>(R.id.save_dialog_name).text.toString()
                story!!.saveWipRecording(name)
            }
            setNegativeButton(R.string.label_discard) { _, _ ->
                story!!.deleteWipRecording()
            }
        }.create().apply {
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            findViewById<View>(R.id.save_dialog_play_button)!!.setOnClickListener {
                player.play(story!!.wipRecording())
            }
            findViewById<View>(R.id.save_dialog_stop_button)!!.setOnClickListener {
                player.stop()
            }
            findViewById<EditText>(R.id.save_dialog_name)!!.addTextChangedListener(afterTextChanged = {
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
            })
        }
    }

    private fun askUserToDeleteStory() {
        AlertDialog.Builder(this).apply {
            setTitle(resources.getString(R.string.delete_dialog_title, title))
            setPositiveButton(R.string.label_delete) { _, _ ->
                story!!.deleteStory()
                finish()
            }
            setNegativeButton(R.string.label_cancel) { _, _ -> }
        }.create().show()
    }

    private fun askUserToRenameStory() {
        AlertDialog.Builder(this).apply {
            setTitle(resources.getString(R.string.rename_dialog_title, title))
            val root = layoutInflater.inflate(R.layout.dialog_rename, null)
            setView(root)
            setPositiveButton(R.string.label_rename) { _, _ ->
                val newName = root.findViewById<EditText>(R.id.rename_dialog_name).text.toString().trim()
                val newDirectory = File(story!!.directory.parentFile, newName)
                story!!.renameStory(newDirectory)
                finish()
                startActivity(Intent(this@StoryEditorActivity, StoryEditorActivity::class.java).apply {
                    data = newDirectory.toUri()
                })
            }
            setNegativeButton(R.string.label_cancel) { _, _ -> }
        }.create().apply {
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            findViewById<EditText>(R.id.rename_dialog_name)!!.addTextChangedListener(afterTextChanged = {
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
            })
        }
    }

    //endregion

    @SuppressLint("ClickableViewAccessibility")  // TODO
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_REQUEST_CODE)
        setContentView(R.layout.activity_story_editor)

        story = Story(intent.data!!.toFile())
        title = story!!.directory.name

        findViewById<Button>(R.id.record_button).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> recorder.start(story!!.wipRecording())
                MotionEvent.ACTION_UP -> {
                    if (recorder.stop()) {
                        askUserToSave()
                    }
                }
            }
            false
        }

        val adapter = TrackAdapter()
        findViewById<RecyclerView>(R.id.track_list).let {
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(this)
            ItemTouchHelper(TrackDragCallback()).attachToRecyclerView(it)
        }
        addSubscription(story!!.updates().subscribe {
            adapter.submitList(it.tracks)
        })
    }

    override fun onDestroy() {
        // Can release() these as this Activity should never be reused after onDestroy()
        recorder.release()
        player.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (!grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.w(TAG, "Warning: Record Audio permission denied")
                finish()
            } else {
                Log.d(TAG, "Record Audio permission granted")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_story_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_rename) {
            askUserToRenameStory()
            return true
        }
        if (item.itemId == R.id.action_delete) {
            askUserToDeleteStory()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
