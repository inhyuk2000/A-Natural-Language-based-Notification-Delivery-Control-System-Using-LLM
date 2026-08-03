package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import android.util.Log
import com.aallam.openai.api.chat.ChatMessage
import com.example.app.presentation.navigation.AppRoutes

class PromptViewModel : ViewModel() {
    val chatMessages = mutableListOf<ChatMessage>()
    var systemMessageAdded = false

    fun resetChat() {
        chatMessages.clear()
        systemMessageAdded = false
        Log.d("Chatlog", "초기화 성공!")
    }
}

/** Legacy entry → Single-Activity shell (chat route) */
class PromptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAV_ROUTE, AppRoutes.CHAT)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
