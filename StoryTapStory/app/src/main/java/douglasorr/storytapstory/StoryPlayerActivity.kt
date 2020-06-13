package douglasorr.storytapstory

import android.annotation.SuppressLint
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.net.toFile
import douglasorr.storytapstory.story.Story
import kotlinx.android.synthetic.main.activity_story_player.*

private const val TAG = "StoryPlayerActivity"

class StoryPlayerActivity : BaseActivity() {
    private val player = Player()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_player)

        (spinning_star.drawable as AnimatedVectorDrawable).start()

        // Go fullscreen
        supportActionBar?.hide()
        frame_player.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        // Load the story
        val story = Story(intent.data!!.toFile())
        addSubscription(story.updates().firstElement().subscribe { data ->
            var trackIterator = data.tracks.iterator()

            // Responsive background
            addSubscription(player.updates().subscribe {
                if (it is Player.Event.Start) {
                    spinning_star.setColorFilter(getColor(R.color.star_tint_playing))
                }
                if (it is Player.Event.End) {
                    spinning_star.setColorFilter(getColor(R.color.star_tint))
                    if (!trackIterator.hasNext()) {
                        // Reset to start
                        // TODO: set a differnet visual
                        trackIterator = data.tracks.iterator()
                    }
                }
            })

            // Click handling
            frame_player.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (!player.isPlaying() && trackIterator.hasNext()) {
                        player.play(story.trackNamed(trackIterator.next()))
                    }
                    true
                } else {
                    false
                }
            }
        })
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
