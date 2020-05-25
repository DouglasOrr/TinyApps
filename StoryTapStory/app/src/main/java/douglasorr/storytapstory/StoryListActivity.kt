package douglasorr.storytapstory

import android.content.Intent
import android.os.Bundle
import android.view.*
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
                onClick(R.id.story_item) { name -> startPlayerActivity(name) }
                onClick(R.id.story_edit) { name -> startEditActivity(name) }
                onClick(R.id.story_rename) { name -> askUserToRename(name) }
                onClick(R.id.story_delete) { name -> askUserToDelete(name) }
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

    //region Actions

    private fun startPlayerActivity(name: String) {
        startActivity(Intent(this, StoryPlayerActivity::class.java).apply {
            data = storyList!!.path(name).toUri()
        })
    }

    private fun startEditActivity(name: String) {
        startActivity(Intent(this, StoryEditorActivity::class.java).apply {
            data = storyList!!.path(name).toUri()
        })
    }

    private fun askUserToCreate() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.create_dialog_title)
            val root = layoutInflater.inflate(R.layout.dialog_create, null)
            setView(root)
            setPositiveButton(R.string.label_create) { _, _ ->
                val name = root.findViewById<EditText>(R.id.create_dialog_name).text.toString().trim()
                storyList!!.create(name)
                startEditActivity(name)
            }
            setNegativeButton(R.string.label_cancel) { _, _ -> }
        }.create().apply {
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            findViewById<EditText>(R.id.create_dialog_name)!!.addTextChangedListener(afterTextChanged = {
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !it.isNullOrBlank()
            })
        }
    }

    private fun askUserToRename(oldName: String) {
        AlertDialog.Builder(this).apply {
            setTitle(resources.getString(R.string.rename_dialog_title, oldName))
            val root = layoutInflater.inflate(R.layout.dialog_rename, null)
            setView(root)
            setPositiveButton(R.string.label_rename) { _, _ ->
                val newName = root.findViewById<EditText>(R.id.rename_dialog_name).text.toString().trim()
                storyList!!.rename(oldName, newName)
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

    private fun askUserToDelete(name: String) {
        AlertDialog.Builder(this).apply {
            setTitle(resources.getString(R.string.delete_dialog_title, name))
            setPositiveButton(R.string.label_delete) { _, _ ->
                storyList!!.delete(name)
            }
            setNegativeButton(R.string.label_cancel) { _, _ -> }
        }.create().show()
    }

    //endregion

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
            adapter.submitList(it.stories.sorted())
        })
    }

    override fun onResume() {
        super.onResume()
        // E.g. if ClipsActivity wins the "story create() race"
        storyList!!.refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_story_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_new_story) {
            askUserToCreate()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
