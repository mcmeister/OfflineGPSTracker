package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var adLayout: View
    private lateinit var proceedButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        adLayout = findViewById(R.id.ad_layout)
        proceedButton = findViewById(R.id.proceed_button)

        // Step 1: Show logo for 2s, then display ad
        window.decorView.postDelayed({
            findViewById<View>(R.id.logo).visibility = View.GONE
            adLayout.visibility = View.VISIBLE

            // Step 2: Simulate ad loading for 3s
            proceedButton.visibility = View.INVISIBLE
            proceedButton.postDelayed({
                proceedButton.visibility = View.VISIBLE
            }, 3000)

        }, 2000)

        proceedButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}