package eu.kanade.translation.translator

import eu.kanade.tachiyomi.network.await
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tachiyomi.core.common.util.system.logcat
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
    private val requestDelayMs: Long = 300L,
    private val maxRetries: Int = 3,
) : TextTranslator {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Birden fazla endpoint — ban olunca sıradakine geç
    private val clients = listOf("gtx", "at", "webapp")
    private var clientIndex = 0

    // Birden fazla User-Agent — rotasyon ile ban azaltma
    private val userAgents = listOf(
        "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 Chrome/90.0.4430.91 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 Chrome/105.0.5195.136 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/114.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
    )
    private var uaIndex = 0

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        pages.forEach { (_, pageTranslation) ->
            pageTranslation.blocks.forEach { block ->
                // \n sorununu çöz: satırları ayrı çevir, sonra birleştir
                val lines = block.text.split("\n").filter { it.isNotBlank() }
                val translatedLines = lines.map { line ->
                    translateWithRetry(line)
                }
                block.translation = translatedLines.joinToString("\n")
                delay(requestDelayMs) // Ban koruması
            }
        }
    }

    private suspend fun translateWithRetry(text: String): String {
        if (text.isBlank()) return text
        var lastError: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val result = doTranslate(text)
                if (result.isNotBlank()) return result
            } catch (e: Exception) {
                lastError = e
                logcat { "Google translate error attempt $attempt: ${e.message}" }
                // Endpoint ve User-Agent değiştir
                clientIndex = (clientIndex + 1) % clients.size
                uaIndex = (uaIndex + 1) % userAgents.size
                delay(500L * (attempt + 1)) // Exponential backoff
            }
        }
        logcat { "Google translate failed after $maxRetries retries: ${lastError?.message}" }
        return text // Başarısız olursa orijinal metni döndür
    }

    private suspend fun doTranslate(text: String): String {
        val encoded = try {
            URLEncoder.encode(text, "utf-8")
        } catch (e: Exception) {
            URLEncoder.encode(text, "UTF-8")
        }
        val client = clients[clientIndex]
        val token = calculateToken(text)
        val url = "https://translate.google.com/translate_a/single" +
            "?client=$client&sl=auto&tl=${toLang.code}" +
            "&dt=t&otf=1&ssel=0&tsel=0&kc=1&tk=$token&q=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgents[uaIndex])
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://translate.google.com/")
            .build()

        val response = okHttpClient.newCall(request).await()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }
        val body = response.body.string()

        return try {
            val arr = JSONArray(body)
            val sb = StringBuilder()
            val translations = arr.getJSONArray(0)
            for (i in 0 until translations.length()) {
                val part = translations.optJSONArray(i)
                if (part != null) {
                    val translated = part.optString(0)
                    if (translated.isNotEmpty() && translated != "null") {
                        sb.append(translated)
                    }
                }
            }
            sb.toString().trim()
        } catch (e: Exception) {
            logcat { "Google parse error: ${e.message}, body: ${body.take(200)}" }
            ""
        }
    }

    private fun calculateToken(str: String): String {
        val list = mutableListOf<Int>()
        var i = 0
        while (i < str.length) {
            val c = str.codePointAt(i)
            when {
                c < 128 -> list.add(c)
                c < 2048 -> { list.add((c shr 6) or 192); list.add((c and 63) or 128) }
                c in 55296..57343 && i + 1 < str.length -> {
                    val n = str.codePointAt(i + 1)
                    if (n in 56320..57343) {
                        val cp = ((c and 1023) shl 10) + (n and 1023) + 65536
                        list.add((cp shr 18) or 240); list.add(((cp shr 12) and 63) or 128)
                        list.add(((cp shr 6) and 63) or 128); list.add((cp and 63) or 128); i++
                    }
                }
                else -> { list.add((c shr 12) or 224); list.add(((c shr 6) and 63) or 128); list.add((c and 63) or 128) }
            }
            i++
        }
        var j: Long = 406644
        for (num in list) j = rl(j + num.toLong(), "+-a^+6")
        var r = rl(j, "+-3^+b+-f") xor 3293161072L
        if (r < 0) r = (r and 2147483647L) + 2147483648L
        val m = r % 1000000L
        return "$m.${406644L xor m}"
    }

    private fun rl(j: Long, str: String): Long {
        var res = j; var i = 0
        while (i < str.length - 2) {
            val shift = if (str[i + 2] in 'a'..'z') str[i + 2].code - 87 else str[i + 2].digitToInt()
            val sv = if (str[i + 1] == '+') res ushr shift else res shl shift
            res = if (str[i] == '+') (res + sv) and 4294967295L else res xor sv
            i += 3
        }
        return res
    }

    override fun close() {}
}
