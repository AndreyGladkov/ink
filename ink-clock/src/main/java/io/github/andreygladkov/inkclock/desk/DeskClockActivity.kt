package io.github.andreygladkov.inkclock.desk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class DeskClockActivity : Activity() {
    private lateinit var desk: DeskView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desk = DeskView(this)
        setContentView(desk)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, desk).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        desk.start()
    }

    override fun onPause() {
        super.onPause()
        desk.stop()
    }

    companion object {
        fun intent(context: Context) = Intent(context, DeskClockActivity::class.java)
    }
}
