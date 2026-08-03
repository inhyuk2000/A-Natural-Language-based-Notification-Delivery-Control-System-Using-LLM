package com.example.app

import android.content.Context
import android.os.Handler
import android.os.Looper

class MyChecker(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            contextManager.periodicDeliveryCheck()
            handler.postDelayed(this, 10_000L) // 10초마다 반복
        }
    }
    val contextManager = ContextManager(context)

    fun startPeriodicCheck() {
        handler.post(runnable)
    }

    fun stopPeriodicCheck() {
        handler.removeCallbacks(runnable)
    }
}
