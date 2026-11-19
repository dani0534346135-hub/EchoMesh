package com.daniel.echomesh

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val engine = AcousticEngine()
    private lateinit var statusText: TextView
    private var isListening = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleListen()
        } else {
            statusText.text = "נדרשת הרשאת מיקרופון לקליטת אותות"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#10141C"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "EchoMesh — 1850 Hz"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "מוכן לשידור וקליטה"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val sendBtn = Button(this).apply {
            text = "שדר פולס (Transmit)"
            setOnClickListener {
                statusText.text = "משדר תדר 1850Hz..."
                engine.transmitPulse(freqHz = 1850.0, durationMs = 600)
            }
        }

        val listenBtn = Button(this).apply {
            text = "הפעל האזנה (Listen)"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    toggleListen()
                    text = if (isListening) "עצור האזנה" else "הפעל האזנה (Listen)"
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(sendBtn)
        root.addView(listenBtn)
        setContentView(root)
    }

    private fun toggleListen() {
        if (!isListening) {
            isListening = true
            engine.startListening(targetFreqHz = 1850.0, threshold = 25.0) { score, detected ->
                val formatted = String.format("%.1f", score)
                if (detected) {
                    statusText.text = "נקלט אות! עוצמה: $formatted"
                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                } else {
                    statusText.text = "מאזין... עוצמה: $formatted"
                    statusText.setTextColor(Color.LTGRAY)
                }
            }
        } else {
            isListening = false
            engine.stopListening()
            statusText.text = "האזנה הופסקה"
            statusText.setTextColor(Color.LTGRAY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stopListening()
    }
}
