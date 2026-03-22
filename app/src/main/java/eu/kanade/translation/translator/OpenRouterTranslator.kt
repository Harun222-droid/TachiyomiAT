package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    val apiKey: String,
    val modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val BATCH_SIZE = 5
    private val MAX_RETRIES = 3

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildSystemPrompt(targetLang: String) = """
        You are a professional manga/manhwa/manhua translator with 10+ years of experience.
        Your goal: translations that feel written by a native $targetLang speaker who understands manga culture.

        CRITICAL RULES:
        1. INPUT: JSON — keys are filenames, values are arrays of text strings.
        2. OUTPUT: Exact same JSON structure — same keys, same array lengths. ONLY valid JSON.
        3. Every input string MUST have exactly one output string at the same index.

        TRANSLATION QUALITY:
        - Sound NATURAL in $targetLang — never robotic or literal.
        - Preserve CHARACTER VOICE: aggressive characters speak bluntly, wise ones calmly.
        - SOUND EFFECTS: Translate to $targetLang equivalent (BOOM, CRASH, SLASH etc).
        - EXCLAMATIONS: Keep intensity level.
        - SHORT TEXTS: Even "!", "...", "Huh?" must be translated correctly.
        - SKILL NAMES / BATTLE CRIES: Keep dramatic, use caps for impact.

        WATERMARKS: Replace any website URLs or scan group names with empty string "".

        Return ONLY valid JSON. No explanation, no markdown.
    """.trimIndent()

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        val pageList = pages.entries.toList()
        for (batchStart in pageList.indices step BATCH_SIZE) {
            val batch = pageList.subList(batchStart, minOf(batchStart + BATCH_SIZE, pageList.size))
            translateBatch(batch, pages)
        }
    }

    private suspend fun translateBatch(
        batch: List<Map.Entry<String, PageTranslation>>,
        pages: MutableMap<String, PageTranslation>,
    ) {
        val data = batch.associate { (k, v) ->
            k to v.blocks.map { it.text.replace("\n", " ").trim() }
        }
        val json = JSONObject(data as Map<*, *>)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val systemPrompt = buildSystemPrompt(toLang.label)

        repeat(MAX_RETRIES) { attempt ->
            try {
                val requestBody = buildJsonObject {
                    put("model", modelName)
                    putJsonObject("response_format") { put("type", "json_object") }
                    put("top_p", 0.95f)
                    put("temperature", temp)
                    put("max_tokens", maxOutputToken)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                        addJsonObject {
                            put("role", "user")
                            put("content", json.toString())
                        }
                    }
                }.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://github.com/TachiyomiAT")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).await()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

                val body = response.body.string()
                val content = JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                val resJson = JSONObject(content)

                for ((k, v) in batch) {
                    val pt = pages[k] ?: continue
                    pt.blocks.forEachIndexed { i, block ->
                        val t = resJson.optJSONArray(k)?.optString(i)
                        block.translation = if (t.isNullOrEmpty() || t == "null") block.text else t
                    }
                    pt.blocks = pt.blocks.filter { it.translation.isNotBlank() }.toMutableList()
                }
                return
            } catch (e: Exception) {
                logcat { "OpenRouter error attempt $attempt: ${e.message}" }
                if (attempt < MAX_RETRIES - 1) {
                    delay(2000L * (attempt + 1))
                } else {
                    logcat { "OpenRouter failed, falling back to Google" }
                    GoogleTranslator(fromLang, toLang)
                        .translate(batch.associate { it.key to it.value }.toMutableMap())
                }
            }
        }
    }

    override fun close() {}
}
