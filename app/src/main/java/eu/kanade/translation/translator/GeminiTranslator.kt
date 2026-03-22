package eu.kanade.translation.translator

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import logcat.logcat
import org.json.JSONObject

class GeminiTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    apiKey: String,
    modelName: String,
    val maxOutputToken: Int,
    val temp: Float,
) : TextTranslator {

    private val BATCH_SIZE = 5
    private val MAX_RETRIES = 3

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
        - SOUND EFFECTS: Translate to $targetLang equivalent (e.g. BOOM, CRASH, SLASH).
        - EXCLAMATIONS: Keep intensity — "IMPOSSIBLE!!" stays explosive.
        - SHORT TEXTS: Even "!", "...", "Huh?" must be translated correctly.
        - SKILL NAMES / BATTLE CRIES: Keep dramatic, use caps for impact.
        - THOUGHT vs SPEECH: Translate both the same way.

        WATERMARKS: Replace any website URLs or scan group names with empty string "".

        Return ONLY valid JSON. No explanation, no markdown.
    """.trimIndent()

    private val model by lazy {
        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            generationConfig = generationConfig {
                topK = 40
                topP = 0.95f
                temperature = temp
                maxOutputTokens = maxOutputToken
                responseMimeType = "application/json"
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
            ),
            systemInstruction = content { text(buildSystemPrompt(toLang.label)) },
        )
    }

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

        repeat(MAX_RETRIES) { attempt ->
            try {
                val response = model.generateContent(json.toString())
                val responseText = response.text ?: return@repeat
                val resJson = JSONObject(responseText)
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
                logcat { "Gemini error attempt $attempt: ${e.message}" }
                if (attempt < MAX_RETRIES - 1) {
                    delay(1500L * (attempt + 1))
                } else {
                    logcat { "Gemini failed, falling back to Google" }
                    GoogleTranslator(fromLang, toLang)
                        .translate(batch.associate { it.key to it.value }.toMutableMap())
                }
            }
        }
    }

    override fun close() {}
}
