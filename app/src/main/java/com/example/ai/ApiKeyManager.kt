package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiKeyManager {
    private const val PREFS_NAME = "omnicampus_ai_config"
    private const val KEY_GEMINI_API_KEY = "user_gemini_api_key"
    private const val TAG = "ApiKeyManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resolves the active Gemini API key in order of priority:
     * 1. User-entered in-app key (stored in private SharedPreferences)
     * 2. BuildConfig.GEMINI_API_KEY (injected by Secrets plugin via .env)
     * 3. System environment variable
     */
    fun getApiKey(context: Context): String? {
        // 1. Check user-configured key
        val userKey = getPrefs(context).getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (!userKey.isNullOrBlank()) {
            return userKey
        }

        // 2. Check BuildConfig
        val buildKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val v = field.get(null) as? String
            if (v.isNullOrBlank() || v == "MY_GEMINI_API_KEY" || v == "null") null else v.trim()
        } catch (_: Exception) {
            null
        }
        if (!buildKey.isNullOrBlank()) {
            return buildKey
        }

        // 3. Check System environment
        val envKey = try {
            System.getenv("GEMINI_API_KEY")?.trim()
        } catch (_: Exception) {
            null
        }
        if (!envKey.isNullOrBlank() && envKey != "MY_GEMINI_API_KEY") {
            return envKey
        }

        return null
    }

    fun hasKeyConfigured(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    fun getUserSavedKey(context: Context): String? {
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, null)
    }

    fun saveUserApiKey(context: Context, apiKey: String) {
        val trimmed = apiKey.trim()
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, trimmed).apply()
        Log.i(TAG, "User Gemini API key updated (${if (trimmed.isNotBlank()) "Length: " + trimmed.length else "Cleared"})")
    }

    fun clearUserApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_GEMINI_API_KEY).apply()
        Log.i(TAG, "User Gemini API key cleared")
    }

    fun getKeySourceDescription(context: Context): String {
        val userKey = getPrefs(context).getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (!userKey.isNullOrBlank()) {
            val masked = if (userKey.length > 8) {
                "${userKey.take(4)}...${userKey.takeLast(4)}"
            } else "••••••••"
            return "Custom Key ($masked)"
        }

        val buildKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val v = field.get(null) as? String
            if (v.isNullOrBlank() || v == "MY_GEMINI_API_KEY" || v == "null") null else v.trim()
        } catch (_: Exception) { null }

        if (!buildKey.isNullOrBlank()) {
            return "Build Configuration (AI Studio Secrets)"
        }

        return "Not Configured"
    }

    data class SmokeTestResult(
        val isSuccess: Boolean,
        val modelUsed: String,
        val latencyMs: Long,
        val prompt: String,
        val responseText: String,
        val errorMessage: String? = null
    )

    /**
     * Validates a Gemini API Key against Google's API endpoint with supported models.
     * Returns Result.success with confirmation message or Result.failure with human-readable error.
     */
    suspend fun validateApiKeyOnline(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key cannot be empty"))
        }

        var lastError = "Validation failed"

        for (model in AiModelConfig.SUPPORTED_MODELS) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val pingPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Respond with 'OK'")
                                })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(pingPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val code = response.code
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    return@withContext Result.success("Verified successfully with $model")
                } else {
                    val errorDetail = try {
                        val json = JSONObject(body)
                        json.optJSONObject("error")?.optString("message", "") ?: body
                    } catch (_: Exception) {
                        body
                    }
                    lastError = when (code) {
                        400 -> "Invalid request or key format ($errorDetail)"
                        403 -> "API Key is invalid, restricted, or unauthorized (HTTP 403)"
                        404 -> "Model $model not found (HTTP 404)"
                        429 -> "Rate limit or quota exceeded (HTTP 429)"
                        else -> "Gemini API error (HTTP $code: $errorDetail)"
                    }
                }
            } catch (e: Exception) {
                lastError = "Network error: ${e.localizedMessage ?: "Unable to reach Google API"}"
            }
        }

        Result.failure(Exception(lastError))
    }

    /**
     * Performs a real text-generation smoke test as required by Phase 2.
     * Sends a small prompt and returns the actual model response and latency.
     */
    suspend fun runSmokeTest(apiKey: String): SmokeTestResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext SmokeTestResult(
                isSuccess = false,
                modelUsed = "None",
                latencyMs = 0L,
                prompt = "Hello Gemini, reply with 'AI Connection Verified'.",
                responseText = "",
                errorMessage = "API key is not configured"
            )
        }

        val testPrompt = "Hello Gemini, reply with 'AI Connection Verified'."
        var lastError = "Smoke test failed"

        for (model in AiModelConfig.SUPPORTED_MODELS) {
            val startTime = System.currentTimeMillis()
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", testPrompt) })
                            })
                        })
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                    val reply = parts?.optJSONObject(0)?.optString("text", "AI Connection Verified") ?: "OK"
                    return@withContext SmokeTestResult(
                        isSuccess = true,
                        modelUsed = model,
                        latencyMs = latency,
                        prompt = testPrompt,
                        responseText = reply.trim()
                    )
                } else {
                    val errJson = try { JSONObject(body).optJSONObject("error") } catch (_: Exception) { null }
                    val serverMsg = errJson?.optString("message", "") ?: body
                    lastError = "HTTP ${response.code} on $model: $serverMsg"
                }
            } catch (e: Exception) {
                lastError = "Network error on $model: ${e.localizedMessage ?: "Connection failure"}"
            }
        }

        SmokeTestResult(
            isSuccess = false,
            modelUsed = AiModelConfig.PRIMARY_MODEL,
            latencyMs = 0L,
            prompt = testPrompt,
            responseText = "",
            errorMessage = lastError
        )
    }
}

