package com.eden.edenbarcode.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import com.eden.edenbarcode.R
import kotlinx.android.synthetic.main.activity_lunch.*
import java.util.concurrent.TimeUnit

private const val ANIMATION_TIME: Long = 1500
private const val ANIMATION_BK_DELAY: Long = 100
private const val START_LOGO_ALPHA = 0.1f
private const val START_BK_ALPHA = 0.0f
private const val FINISH_BK_ALPHA = 0.3f

class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lunch)
        Handler().postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, TimeUnit.SECONDS.toMillis(2))
    }

    public override fun onStart() {
        super.onStart()

        val fadeInAnimation = ObjectAnimator.ofFloat(splash_logo, View.ALPHA, START_LOGO_ALPHA, 1.0f)
        fadeInAnimation.duration = ANIMATION_TIME
        fadeInAnimation.start()

        val fadeOutAnimation = ObjectAnimator.ofFloat(
            background_splash, View.ALPHA,
            START_BK_ALPHA,
            FINISH_BK_ALPHA
        )
        //fadeOutAnimation.setInterpolator(new AccelerateInterpolator());
        fadeOutAnimation.duration = ANIMATION_TIME
        fadeOutAnimation.startDelay = ANIMATION_BK_DELAY
        fadeOutAnimation.start()
    }
}
