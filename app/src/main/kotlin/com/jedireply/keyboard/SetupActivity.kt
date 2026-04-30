package com.jedireply.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            setBackgroundColor(0xFF1C1C1E.toInt())
        }

        val title = TextView(this).apply {
            text = "⚡ JediChat AI Keyboard"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }

        val subtitle = TextView(this).apply {
            text = "AI-powered keyboard that learns your style"
            textSize = 14f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, 0, 0, 32)
        }

        val step1 = TextView(this).apply {
            text = "Step 1: Enable JediChat Keyboard"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }

        val enableBtn = Button(this).apply {
            text = "Enable in Settings"
            setBackgroundColor(0xFF007AFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 24)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val step2 = TextView(this).apply {
            text = "Step 2: Enter your JediChatAI token"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 8)
        }

        val tokenInput = EditText(this).apply {
            hint = "Paste your token here"
            setHintTextColor(0xFF636366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2E.toInt())
            setPadding(16, 16, 16, 16)
        }

        val prefs = getSharedPreferences("jedikeyboard", Context.MODE_PRIVATE)
        tokenInput.setText(prefs.getString("token", ""))

        val saveBtn = Button(this).apply {
            text = "Save Token"
            setBackgroundColor(0xFF34C759.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 16, 0, 0)
            setOnClickListener {
                prefs.edit().putString("token", tokenInput.text.toString()).apply()
                Toast.makeText(context, "✅ Token saved!", Toast.LENGTH_SHORT).show()
            }
        }

        val step3 = TextView(this).apply {
            text = "Step 3: Select JediChat as your keyboard"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 8)
        }

        val selectBtn = Button(this).apply {
            text = "Switch to JediChat Keyboard"
            setBackgroundColor(0xFF5856D6.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(step1)
        layout.addView(enableBtn)
        layout.addView(step2)
        layout.addView(tokenInput)
        layout.addView(saveBtn)
        layout.addView(step3)
        layout.addView(selectBtn)

        setContentView(layout)
    }
}
