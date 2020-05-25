package douglasorr.storytapstory

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import douglasorr.storytapstory.story.StoryList
import java.io.File

private const val TAG = "StoryListActivity"
private const val FOLDER_NAME = "stories"

class StoryListActivity : BaseActivity() {

    private var storyList: StoryList? = null

    //region ListAdapter

    object StoryDiffer : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    inner class StoryListAdapter : ListAdapter<String, StoryListAdapter.ViewHolder>(StoryDiffer) {
        inner class ViewHolder(val root: ViewGroup): RecyclerView.ViewHolder(root) {
            var name: String? = null
            val title: TextView = root.findViewById(R.id.story_title)

            init {
                onClick(R.id.story_item) { name ->
                    Log.d(TAG, "Open $name - TODO")
                }
                onClick(R.id.story_edit) { name ->
                    startActivity(Intent(this@StoryListActivity, ClipsActivity::class.java).apply {
                        data = storyList!!.path(name).toUri()
                    })
                }
                onClick(R.id.story_rename) { name ->
                    askUserToRename(name)
                }
                onClick(R.id.story_delete) { name ->
                    Log.d(TAG, "Delete $name - TODO")
                }
            }

            fun onClick(id: Int, action: (String) -> Unit) {
                root.findViewById<View>(id).setOnClickListener {
                    // Don't do anything if name is null
                    name?.let { action(it) }
                }
            }

            fun bind(name: String) {
                this.name = name
                title.text = name
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(inflater.inflate(R.layout.list_item_story, parent, false) as ViewGroup)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    //endregion

    fun askUserToRename(oldName: String) {
        AlertDialog.Builder(this).apply {
            setTitle(resources.getString(R.string.rename_dialog_title, oldName))
            val root = layoutInflater.inflate(R.layout.dialog_rename, null)
            setView(root)
            setPositiveButton(R.string.label_rename) { _, _ ->
                val newName = root.findViewById<EditText>(R.id.rename_dialog_name).text.toString().trim()
                storyList!!.rename(oldName, newName)
            }
            setNegativeButton(R.string.label_cancel) { _, _ -> } // TODO - unnecessary?
        }.create().apply {
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            findViewById<EditText>(R.id.rename_dialog_name)!!.addTextChangedListener(afterTextChanged = {
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_list)
        storyList = StoryList(File(getExternalFilesDir(null), FOLDER_NAME))

        val adapter = StoryListAdapter()
        findViewById<RecyclerView>(R.id.story_list).let { view ->
            view.adapter = adapter
            view.layoutManager = LinearLayoutManager(this)
        }
        addSubscription(storyList!!.updates().subscribe {
            adapter.submitList(it.stories)
        })
    }
}
