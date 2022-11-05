package com.banuba.sdk.example

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.banuba.sdk.example.offscreen.OffscreenActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startFaceAR.setOnClickListener {
            startActivity(Intent(applicationContext, FaceArActivity::class.java))
        }

        startOffscreenFaceAR.setOnClickListener {
            startActivity(Intent(applicationContext, OffscreenActivity::class.java))
        }
    }
}