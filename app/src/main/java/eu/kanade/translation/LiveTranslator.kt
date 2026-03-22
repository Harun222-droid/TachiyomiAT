package eu.kanade.translation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.model.TranslationBlock
import eu.kanade.translation.recognizer.TextRecognizer
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs

/**
 * Canlı çeviri motoru — bitmap alır, OCR+çeviri yapıp PageTranslation döner.
 * İndirme gerekmez, okurken arka planda çalışır.
 */
class LiveTranslator(
    private val prefs: TranslationPreferences = Injekt.get(),
) {
    private var recognizer: TextRecognizer? = null
    private var lastFromLang: TextRecognizerLanguage? = null

    suspend fun translate(bitmap: Bitmap, pageKey: String): PageTranslation? =
        withContext(Dispatchers.Default) {
            try {
                val fromLang = TextRecognizerLanguage.fromPref(prefs.translateFromLanguage())
                val toLang = TextTranslatorLanguage.fromPref(prefs.translateToLanguage())

                // Recognizer'ı gerekirse yeniden oluştur
                if (recognizer == null || lastFromLang != fromLang) {
                    recognizer?.close()
                    recognizer = TextRecognizer(fromLang, prefs)
                    lastFromLang = fromLang
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer!!.recognize(image)

                val blocks = result.textBlocks.filter {
                    it.boundingBox != null && it.text.length > 1
                }

                if (blocks.isEmpty()) return@withContext null

                val translation = PageTranslation(
                    imgWidth = bitmap.width.toFloat(),
                    imgHeight = bitmap.height.toFloat(),
                )

                for (block in blocks) {
                    val bounds = block.boundingBox!!
                    val symBounds = runCatching {
                        block.lines.first().elements.first().symbols.first().boundingBox!!
                    }.getOrNull() ?: bounds

                    // Arka plan rengini hesapla
                    val (bgColor, textColor) = extractColors(bitmap, bounds)

                    translation.blocks.add(
                        TranslationBlock(
                            text = block.text,
                            width = bounds.width().toFloat(),
                            height = bounds.height().toFloat(),
                            symWidth = symBounds.width().toFloat(),
                            symHeight = symBounds.height().toFloat(),
                            angle = block.lines.first().angle,
                            x = bounds.left.toFloat(),
                            y = bounds.top.toFloat(),
                            bgColorArgb = bgColor,
                            textColorArgb = textColor,
                        ),
                    )
                }

                // Blokları birleştir
                translation.blocks = smartMerge(translation.blocks)

                // Çeviri yap
                val engine = TextTranslators.fromPref(prefs.translationEngine())
                val translator = engine.build(prefs, fromLang, toLang)
                val pagesMap = mutableMapOf(pageKey to translation)
                translator.translate(pagesMap)
                translator.close()

                translation
            } catch (e: Exception) {
                android.util.Log.e("LiveTranslator", "Error: ${e.message}")
                null
            }
        }

    private fun extractColors(bitmap: Bitmap, bounds: android.graphics.Rect): Pair<Int, Int> {
        return try {
            val left = bounds.left.coerceIn(0, bitmap.width - 1)
            val top = bounds.top.coerceIn(0, bitmap.height - 1)
            val right = bounds.right.coerceIn(left + 1, bitmap.width)
            val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)
            val stepX = maxOf(1, (right - left) / 10)
            val stepY = maxOf(1, (bottom - top) / 10)
            var r = 0L; var g = 0L; var b = 0L; var count = 0
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val p = bitmap.getPixel(x, y)
                    r += android.graphics.Color.red(p)
                    g += android.graphics.Color.green(p)
                    b += android.graphics.Color.blue(p)
                    count++
                    x += stepX
                }
                y += stepY
            }
            val ar = (r / count).toInt()
            val ag = (g / count).toInt()
            val ab = (b / count).toInt()
            val bg = android.graphics.Color.argb(235, ar, ag, ab)
            val lum = 0.299 * ar + 0.587 * ag + 0.114 * ab
            val text = if (lum > 128) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            Pair(bg, text)
        } catch (e: Exception) {
            Pair(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        }
    }

    private fun smartMerge(blocks: MutableList<TranslationBlock>): MutableList<TranslationBlock> {
        if (blocks.size <= 1) return blocks
        val merged = mutableListOf<TranslationBlock>()
        val used = BooleanArray(blocks.size)
        for (i in blocks.indices) {
            if (used[i]) continue
            var cur = blocks[i]
            for (j in (i + 1) until blocks.size) {
                if (used[j]) continue
                if (shouldMerge(cur, blocks[j])) {
                    cur = merge(cur, blocks[j])
                    used[j] = true
                }
            }
            merged.add(cur)
            used[i] = true
        }
        return merged.toMutableList()
    }

    private fun shouldMerge(a: TranslationBlock, b: TranslationBlock): Boolean {
        val widthSimilar = abs(a.width - b.width) < 50 || b.width < a.width
        val xClose = abs(a.x - b.x) < 30
        val yClose = (b.y - (a.y + a.height)) < 30
        return widthSimilar && xClose && yClose
    }

    private fun merge(a: TranslationBlock, b: TranslationBlock): TranslationBlock {
        val x = minOf(a.x, b.x)
        val y = a.y
        val w = maxOf(a.x + a.width, b.x + b.width) - x
        val h = maxOf(a.y + a.height, b.y + b.height) - y
        return TranslationBlock(
            text = "${a.text} ${b.text}",
            width = w, height = h, x = x, y = y,
            symHeight = a.symHeight, symWidth = a.symWidth, angle = a.angle,
            bgColorArgb = a.bgColorArgb, textColorArgb = a.textColorArgb,
        )
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
