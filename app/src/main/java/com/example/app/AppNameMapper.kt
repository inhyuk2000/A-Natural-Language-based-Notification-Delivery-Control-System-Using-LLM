package com.example.app

import android.content.Context

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 기기 설치 앱의 PackageManager 라벨만 수집한다 (수동 alias 없음).
 * 이름→package 해석은 FastAPI가 cosine으로 수행하고, Android는 packages[]를 DB에 저장한다.
 * [toPackageName]은 서버 packages가 없을 때의 fallback / UI용 exact match이다.
 */

object AppNameMapper {
    private val mapping: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun logCurrentMapping() {
        mapping.forEach { (pkgName, nameList) ->
            android.util.Log.d("AppNameMapper", "Package: $pkgName -> Names: $nameList")
        }
    }

    /** Chat UI 참고용: { "com.foo": ["라벨"] } */
    fun getMappingAsJson(): String {
        val jsonObject = JsonObject(
            mapping.mapValues { (_, nameList) ->
                JsonArray(nameList.map { JsonPrimitive(it) })
            },
        )
        return jsonObject.toString()
    }

    /**
     * FastAPI `/v1/extract-rule` 용:
     * `[{ "packageName": "...", "labels": ["..."] }, ...]` 생성해주는 헬퍼 함수
     */
    fun getInstalledAppsJsonArray(): JsonArray {
        return buildJsonArray {
            mapping.forEach { (pkg, labels) ->
                add(
                    buildJsonObject {
                        put("packageName", pkg)
                        putJsonArray("labels") {
                            labels.forEach { add(JsonPrimitive(it)) }
                        }
                    },
                )
            }
        }
    }

    fun loadInstalledApps(context: Context) {
        mapping.clear()
        val packageManager = context.packageManager
        val packages = packageManager.getInstalledApplications(0)
        // .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // 시스템 앱 제외 시 주석 해제

        packages.forEach { appInfo ->
            val appName = packageManager.getApplicationLabel(appInfo).toString().trim()
            val packageName = appInfo.packageName
            if (appName.isEmpty()) return@forEach
            val desc = appInfo.loadDescription(packageManager)?.toString()
            val category = appInfo.category
            android.util.Log.d(
                "AppMeta",
                "$packageName label=$appName desc=$desc category=$category",
            )
            mapping.getOrPut(packageName) { mutableListOf() }.apply {
                if (none { it.equals(appName, ignoreCase = true) }) add(appName)
            }
        }

        android.util.Log.d("AppNameMapper", "✅ 자동 앱 라벨 로드 완료 (${mapping.size}개)")
    }

    /** 패키지명 → 표시 라벨 (없으면 packageName) */
    fun toDisplayName(packageName: String): String {
        return mapping[packageName]?.firstOrNull() ?: packageName
    }

    /**
     * 표시 이름 → packageName (대소문자 무시 exact match).
     * 서버 cosine 매핑이 주 경로이므로 fallback 전용.
     */
    fun toPackageName(userInput: String): String? {
        val normalizedInput = userInput.lowercase().trim()
        if (normalizedInput.isEmpty()) return null
        return mapping.entries.firstOrNull { (_, names) ->
            names.any { it.lowercase() == normalizedInput }
        }?.key
    }

    fun getAllAppNames(): List<String> {
        return mapping.values.flatten()
    }
}
