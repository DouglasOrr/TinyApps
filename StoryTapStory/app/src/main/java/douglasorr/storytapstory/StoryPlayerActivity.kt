package douglasorr.storytapstory

import android.annotation.SuppressLint
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.net.toFile
import androidx.core.view.isVisible
import douglasorr.storytapstory.story.Story
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_story_player.*
import java.util.concurrent.TimeUnit

private const val TAG = "StoryPlayerActivity"
private const val COUNTDOWN_SECONDS = 5

class StoryPlayerActivity : BaseActivity() {
    class Controller(val story: Story, val tracks: List<String>) {
        enum class EventType {
            WAITING,
            PLAYING,
            END,
        }
        class Event(val type: EventType, val countdown: Int?)

        private val player = Player()
        private var currentTrack: Int = 0
        private val subject: BehaviorSubject<Event> = BehaviorSubject.createDefault(Event(EventType.WAITING, null))
        private val subscription = player.updates().subscribe {
            if (it is Player.Event.End) {
                currentTrack += 1
                if (currentTrack == tracks.size) {
                    Observable.intervalRange(0, 1 + COUNTDOWN_SECONDS.toLong(), 0, 1, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { count ->
                                val remaining = (COUNTDOWN_SECONDS - count).toInt()
                                subject.onNext(Event(EventType.END, remaining))
                            },
                            { logNonFatal(TAG, "Interval error - should not happen", it)},
                            {
                                currentTrack = 0
                                subject.onNext(Event(EventType.WAITING, null))
                            }
                        )
                } else {
                    subject.onNext(Event(EventType.WAITING, null))
                }
            }
        }

        fun updates(): Observable<Event> = subject

        fun play() {
            if (!player.isPlaying() && tracks.isNotEmpty() && currentTrack < tracks.size) {
                player.play(story.trackNamed(tracks[currentTrack]))
                subject.onNext(Event(EventType.PLAYING, null))
            }
        }

        fun stop() {
            player.stop()
            subject.onNext(Event(EventType.WAITING, null))
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
                when (it.type) {
                    Controller.EventType.PLAYING -> {
                        end_view.visibility = View.GONE
                        spinning_star.setColorFilter(getColor(R.color.star_tint_playing))
                    }
                    Controller.EventType.WAITING -> {
                        end_view.visibility = View.GONE
                        spinning_star.setColorFilter(getColor(R.color.star_tint))
                    }
                    Controller.EventType.END -> {
                        if (!end_view.isVisible) {
                            end_view.visibility = View.VISIBLE
                            (star_field.drawable as StarrySkyDrawable).refresh()
                        }
                        end_countdown.text = it.countdown.toString()
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
