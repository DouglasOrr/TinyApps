package douglasorr.storytapstory

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.*
import douglasorr.storytapstory.story.Story
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File

private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 200
private const val TAG = "ClipsActivity"

class ClipsActivity : AppCompatActivity() {
    private val subscriptions = mutableListOf<Disposable>()
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

        fun stop() {
            recorder.apply {
                stop()
                reset()
            }
        }

        fun release() {
            recorder.release()
        }
    }

    class Player {
        private val player = MediaPlayer()

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

    object Differ : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP + ItemTouchHelper.DOWN, 0) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val src = viewHolder as TrackAdapter.ViewHolder
            src.name?.let { story!!.move(it, target.adapterPosition) }
            return true  // assume it will happen, asynchronously
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            throw AssertionError("We don't support swiping")
        }
    }

    inner class TrackAdapter : ListAdapter<String, TrackAdapter.ViewHolder>(Differ) {
        inner class ViewHolder(root: ViewGroup): RecyclerView.ViewHolder(root) {
            var name: String? = null
            val title: TextView = root.findViewById(R.id.track_title)

            init {
                root.findViewById<Button>(R.id.track_play).setOnClickListener {
                    name?.let { player.play(story!!.fileNamed(it)) }
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

    @SuppressLint("ClickableViewAccessibility")  // TODO
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clips)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_REQUEST_CODE)

        this.story = Story(File(getExternalFilesDir(null), "stories/example"))

        fun askUserToSave() {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.save_dialog_title)
                val root = layoutInflater.inflate(R.layout.dialog_save, null)
                setView(root)
                setPositiveButton(R.string.save_dialog_save) { _, _ ->
                    val name = root.findViewById<EditText>(R.id.save_dialog_name).text.toString()
                    Log.d(TAG, "Saving new recording as $name")
                    story!!.saveRecorded(name)
                }
                setNegativeButton(R.string.save_dialog_discard) { _, _ ->
                    Log.d(TAG, "Discarding new recording")
                    story!!.deleteRecorded()
                }
            }.create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                findViewById<Button>(R.id.save_dialog_play_button)!!.setOnClickListener {
                    player.play(story!!.wipRecording())
                }
                findViewById<Button>(R.id.save_dialog_stop_button)!!.setOnClickListener {
                    player.stop()
                }
                findViewById<EditText>(R.id.save_dialog_name)!!.addTextChangedListener(afterTextChanged = {
                    getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
                })
            }
        }

        findViewById<Button>(R.id.record_button).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> recorder.start(story!!.wipRecording())
                MotionEvent.ACTION_UP -> {
                    recorder.stop()
                    askUserToSave()
                }
            }
            false
        }

        val adapter = TrackAdapter()
        val layoutManager = LinearLayoutManager(this)
        findViewById<RecyclerView>(R.id.clip_list).apply {
            this.adapter = adapter
            this.layoutManager = layoutManager
            ItemTouchHelper(DragCallback()).attachToRecyclerView(this)
        }
        subscriptions.add(story!!.updates().subscribe {
            Log.d(TAG, "New data: $it")
            adapter.submitList(it.tracks)
        })
    }

    override fun onDestroy() {
        subscriptions.forEach { it.dispose() }
        subscriptions.clear()
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
                Log.d(TAG, "Record Audio permission denied")
                finish()
            } else {
                Log.d(TAG, "Record Audio permission granted")
            }
        }
    }
}
