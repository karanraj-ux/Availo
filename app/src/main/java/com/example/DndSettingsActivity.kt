package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class DndSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
