package com.example.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

class KeywordActivity : AppCompatActivity() {
    private lateinit var keywordListView: ListView
    private val receivedKeywords = mutableSetOf<String>() // 기존 키워드 목록
    private lateinit var deviceIdTextView: TextView
    private lateinit var realtimeDatabase: FirebaseDatabase
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyword)

        deviceIdTextView = findViewById(R.id.tvDeviceId)
//        realtimeDatabase = FirebaseDatabase.getInstance()
        // Retrieve the device ID from shared preferences and display it
        val sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        deviceId = sharedPreferences.getString("deviceId", null) ?: "UnknownDeviceId"

        displayDeviceId(deviceId)

        keywordListView = findViewById(R.id.keywordListView)
        loadKeywordsFromSharedPreferences() // 저장된 키워드 로드

        findViewById<ImageButton>(R.id.addTextButton).setOnClickListener {
            showAddTextDialog()
        }

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish() // Close the activity and go back
        }

        updateKeywordListView()
    }

    @SuppressLint("SetTextI18n")
    private fun displayDeviceId(deviceId: String) {
        deviceIdTextView.text = "Device ID: $deviceId"
    }

    private fun showAddTextDialog() {
        val input = EditText(this).apply {
            hint = "추가할 키워드를 입력하세요"
        }

        AlertDialog.Builder(this)
            .setTitle("키워드 추가")
            .setMessage("알림에 사용할 키워드를 입력하세요.")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val keyword = input.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    receivedKeywords.add(keyword)
                    saveKeywordsToSharedPreferences() // SharedPreferences에 저장
                    updateKeywordListView()
                    Toast.makeText(this, "키워드가 추가되었습니다: $keyword", Toast.LENGTH_SHORT).show()

                    // 키워드가 추가된 후 Broadcast 전송
                    val intent = Intent("com.example.KEYWORDS_UPDATED")
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateKeywordListView() {
        val keywordList = receivedKeywords.toList()

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, keywordList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK) // 텍스트 색을 검정으로 설정
                return view
            }
        }

        keywordListView.adapter = adapter

        keywordListView.setOnItemClickListener { _, _, position, _ ->
            val keywordToRemove = keywordList[position]
            AlertDialog.Builder(this)
                .setTitle("키워드 삭제")
                .setMessage("정말로 키워드 \"$keywordToRemove\" 를 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    receivedKeywords.remove(keywordToRemove)
                    saveKeywordsToSharedPreferences()
                    updateKeywordListView()
                    Toast.makeText(this, "키워드가 삭제되었습니다: $keywordToRemove", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun saveKeywordsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putStringSet("savedKeywords", receivedKeywords) // Corrected key to "savedKeywords"
        }

        // Send broadcast to notify MainActivity of the keyword update
        val intent = Intent("com.example.KEYWORDS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun loadKeywordsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        receivedKeywords.clear()
        receivedKeywords.addAll(sharedPreferences.getStringSet("savedKeywords", emptySet()) ?: emptySet()) // Corrected key to "savedKeywords"
    }


}