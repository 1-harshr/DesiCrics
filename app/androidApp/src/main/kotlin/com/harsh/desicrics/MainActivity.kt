package com.harsh.desicrics

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** The path of a link the app was opened with, e.g. "/join/AB3XY9", until the app has handled it. */
    private val link = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) link.value = intent.linkPath()

        setContent {
            App(link = link.value, onLinkHandled = { link.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.linkPath()?.let { link.value = it }
    }

    private fun Intent.linkPath(): String? = data?.takeIf { action == Intent.ACTION_VIEW }?.path
}
