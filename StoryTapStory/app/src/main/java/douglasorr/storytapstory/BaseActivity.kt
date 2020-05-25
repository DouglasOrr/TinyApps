package douglasorr.storytapstory

import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.disposables.Disposable

/**
 * Common boring all-activities stuff.
 */
abstract class BaseActivity : AppCompatActivity() {
    private val subscriptions = mutableListOf<Disposable>()

    protected fun addSubscription(d: Disposable) {
        subscriptions.add(d)
    }

    override fun onDestroy() {
        subscriptions.forEach { it.dispose() }
        subscriptions.clear()
        super.onDestroy()
    }
}