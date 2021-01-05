package douglasorr.storytapstory

import android.annotation.SuppressLint
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.net.toFile
import douglasorr.storytapstory.story.Story
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_story_player.*

private const val TAG = "StoryPlayerActivity"

class StoryPlayerActivity : BaseActivity() {
    class Controller(val story: Story, val tracks: List<String>) {
        enum class Event {
            WAITING,
            PLAYING,
            END,
        }
        private val player = Player()
        private var currentTrack: Int = 0
        private val subject: BehaviorSubject<Event> = BehaviorSubject.createDefault(Event.WAITING)
        private val subscription = player.updates().subscribe {
            if (it is Player.Event.End) {
                currentTrack = (currentTrack + 1) % tracks.size
                subject.onNext(if (currentTrack == 0) Event.END else Event.WAITING)
            }
        }

        fun updates(): Observable<Event> = subject

        fun play() {
            if (!player.isPlaying() && tracks.isNotEmpty()) {
                player.play(story.trackNamed(tracks[currentTrack]))
                subject.onNext(Event.PLAYING)
            }
        }

        fun stop() {
            player.stop()
            subject.onNext(Event.WAITING)
        }

        fun release() {
            player.release()
            subscription.dispose()
        }
    }

    private lateinit var controller: Controller

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story_player)

        // Set up animations
        star_field.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        star_field.setImageDrawable(StarrySkyDrawable(getColor(R.color.player_background)))
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
            controller = Controller(story, data.tracks)

            // Responsive background
            addSubscription(controller.updates().subscribe {
                when (it) {
                    Controller.Event.PLAYING -> {
                        star_field.visibility = View.GONE
                        spinning_star.setColorFilter(getColor(R.color.star_tint_playing))
                    }
                    Controller.Event.WAITING -> {
                        star_field.visibility = View.GONE
                        spinning_star.setColorFilter(getColor(R.color.star_tint))
                    }
                    Controller.Event.END -> {
                        star_field.visibility = View.VISIBLE
                        (star_field.drawable as StarrySkyDrawable).refresh()
                    }
                }
            })

            // Click handling
            frame_player.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    controller.play()
                    true
                } else {
                    false
                }
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val starrySky = star_field.drawable as StarrySkyDrawable
        val spinningStar = spinning_star.drawable as AnimatedVectorDrawable
        if (hasFocus) {
            starrySky.start()
            spinningStar.start()
        } else {
            starrySky.stop()
            spinningStar.stop()
            controller.stop()
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
