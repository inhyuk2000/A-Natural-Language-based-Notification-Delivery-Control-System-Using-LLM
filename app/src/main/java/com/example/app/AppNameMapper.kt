package com.example.app

import android.content.Context
import android.content.pm.ApplicationInfo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object AppNameMapper {
    // 앱 이름과 패키지명 매핑
    private val mapping: MutableMap<String, MutableList<String>> = mutableMapOf()

    private fun addManualDefaults() {
        mapping["com.kakao.talk"] = mutableListOf("카카오톡", "카톡", "톡")
        mapping["com.google.android.gm"] = mutableListOf("지메일", "Gmail", "gmail")
        mapping["com.instagram.android"] = mutableListOf("인스타그램", "Instagram", "인스타")
        mapping["com.facebook.katana"] = mutableListOf("페이스북", "Facebook", "페북")
        mapping["com.naver.line.android"] = mutableListOf("라인", "Line")
        mapping["com.samsung.android.messaging"] = mutableListOf("메시지", "Samsung Messages")
        mapping["com.samsung.android.calendar"] = mutableListOf("캘린더")
    }

    fun logCurrentMapping() {
        mapping.forEach { (pkgName, nameList) ->
            android.util.Log.d("AppNameMapper", "Package: $pkgName -> Names: $nameList")
        }
    }

    fun getMappingAsJson(): String {
        val jsonObject = JsonObject(
            mapping.mapValues { (_, nameList) ->
                JsonArray(nameList.map { JsonPrimitive(it) })
            }
        )
        return jsonObject.toString()
    }

    fun loadInstalledApps(context: Context) {
        // 1️⃣ 먼저 수동 등록
        addManualDefaults()

        // 2️⃣ 시스템/사용자 앱 자동 등록
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledApplications(0)
        //.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // 시스템 앱 제외하려면 주석 해제

        packages.forEach { appInfo ->
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val packageName = appInfo.packageName

            // 이미 수동 등록된 앱은 건너뜀
            if (mapping.containsKey(packageName)) return@forEach

            mapping.getOrPut(packageName) { mutableListOf() }.apply {
                if (!contains(appName)) add(appName)
            }
        }

        android.util.Log.d("AppNameMapper", "✅ 앱 매핑 로드 완료 (${mapping.size}개)")
    }

    /**
     * 패키지명을 한글/영문 앱 이름으로 변환
     * ex) "com.kakao.talk" -> "카카오톡"
     */

    fun toDisplayName(packageName: String): String {
        return mapping[packageName]?.firstOrNull() ?: packageName
    }

    /**
     * 사용자가 입력한 문자열을 패키지명으로 변환
     * ex) "카톡" -> "com.kakao.talk"
     */
    fun toPackageName(userInput: String): String? {
        val normalizedInput = userInput.lowercase()
        return mapping.entries.firstOrNull { (_, names) ->
            names.any { it.lowercase() == normalizedInput }
        }?.key
    }

    /**
     * 모든 앱 이름 리스트 반환
     */
    fun getAllAppNames(): List<String> {
        return mapping.values.flatten()
    }
}