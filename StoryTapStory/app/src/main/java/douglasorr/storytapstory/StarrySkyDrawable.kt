package douglasorr.storytapstory

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.util.*
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "StarrySkyDrawable"

private const val NUM_STARS = 40
private const val FRAME_TIME = 20  // ms
private const val TWINKLE_DURATION = 50  // multiples of FRAME_TIME ms
private const val TWINKLE_DELAY = 1000  // multiples of FRAME_TIME ms (per star)

private fun createTriangle(vertices: FloatArray, colors: IntArray, vOffset: Int,
                           ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, color: Int) {
    vertices[2 * vOffset] = ax
    vertices[2 * vOffset + 1] = ay
    colors[vOffset] = color

    vertices[2 * vOffset + 2] = bx
    vertices[2 * vOffset + 3] = by
    colors[vOffset + 1] = color

    vertices[2 * vOffset + 4] = cx
    vertices[2 * vOffset + 5] = cy
    colors[vOffset + 2] = color
}

private fun createStar(vertices: FloatArray, colors: IntArray, vOffset: Int, cx: Float, cy: Float, size: Float, color: Int) {
    val inner = size / 5
    val outer = size / 2
    // left
    createTriangle(vertices, colors, vOffset + 0, cx, cy - inner, cx, cy + inner, cx - outer, cy, color)
    // right
    createTriangle(vertices, colors, vOffset + 3, cx, cy - inner, cx, cy + inner, cx + outer, cy, color)
    // up
    createTriangle(vertices, colors, vOffset + 6, cx - inner, cy, cx + inner, cy, cx, cy - outer, color)
    // down
    createTriangle(vertices, colors, vOffset + 9, cx - inner, cy, cx + inner, cy, cx, cy + outer, color)
}

private class StarMap(val width: Int, val height: Int, private val random: Random) {
    private val scale = min(width, height).toFloat()
    private val paint = Paint().apply { setARGB(255, 255, 255, 255) }

    // Constants
    private val xs = FloatArray(NUM_STARS) { (width / scale) * random.nextFloat() }
    private val ys = FloatArray(NUM_STARS) { (height / scale) * random.nextFloat() }
    private val sizes = FloatArray(NUM_STARS) { .02f + .06f * random.nextFloat().pow(3) }

    // State
    private val nextTwinkle = IntArray(NUM_STARS) { nextTwinkleDelay() }
    private val currentTwinkle = IntArray(NUM_STARS) { 0 }

    // Buffers (cached)
    private val vertices = FloatArray(NUM_STARS * 4 * 3 * 2)
    private val colors = IntArray(vertices.size)

    init {
        for (i in 0 until NUM_STARS) {
            updateStar(i)
        }
    }

    // Sample from an exponential distribution, with rate 1/twinkleDelay
    private fun nextTwinkleDelay() = (TWINKLE_DELAY * -kotlin.math.ln(0.01 + 0.99 * random.nextFloat())).toInt()

    private fun updateStar(i: Int) {
        val twinkle = currentTwinkle[i]
        val stage = if (twinkle < TWINKLE_DURATION / 2) {
            (2f * twinkle) / TWINKLE_DURATION
        } else {
            2f - (2f * twinkle) / TWINKLE_DURATION
        }
        val intensity = .4f + .6f * stage.pow(.5f)
        val color = 0xffffff00.toInt() + (255 * intensity).toInt()
        val size = sizes[i] * (1 + stage)
        createStar(vertices, colors, i * 4 * 3, xs[i], ys[i], size, color)
    }

    fun draw(canvas: Canvas) {
        canvas.scale(scale, scale)
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            vertices.size,
            vertices,
            0,
            null,
            0,
            colors,
            0,
            null,
            -1,
            0,
            paint
        )
    }

    fun tickAnimation() {
        for (i in 0 until NUM_STARS) {
            if (currentTwinkle[i] != 0) {
                --currentTwinkle[i]
                updateStar(i)
            } else if (--nextTwinkle[i] == 0) {
                currentTwinkle[i] = TWINKLE_DURATION
                nextTwinkle[i] = TWINKLE_DELAY / 2 + random.nextInt(TWINKLE_DELAY)
                updateStar(i)
            }
        }
    }
}

class StarrySkyDrawable(val backgroundColor: Int) : Drawable(), Runnable {
    private var refresh = true
    private var stars = StarMap(1, 1, Random())

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (refresh || width != stars.width || height != stars.height) {
            stars = StarMap(width, height, Random())
            refresh = false
        }
        canvas.drawColor(backgroundColor)
        stars.draw(canvas)
    }

    fun start() {
        unscheduleSelf(this)
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_TIME)
    }

    fun stop() {
        unscheduleSelf(this)
    }

    fun refresh() {
        refresh = true
        invalidateSelf()
    }

    override fun run() {
        stars.tickAnimation()
        invalidateSelf()
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_TIME)
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun setAlpha(alpha: Int) {
        // Not implemented
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not implemented
    }
}
