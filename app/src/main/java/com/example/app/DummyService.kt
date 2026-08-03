package com.example.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class DummyService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DummyService", "백그라운드 유지 중")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DummyService", "❌ DummyService 종료됨 - 앱 백그라운드로 추정")
    }
}
