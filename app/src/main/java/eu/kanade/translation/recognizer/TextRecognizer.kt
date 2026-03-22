package eu.kanade.translation.recognizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable

class TextRecognizer(
    val language: TextRecognizerLanguage,
    private val prefs: TranslationPreferences = Injekt.get(),
) : Closeable {

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    fun recognize(image: InputImage): Text {
        val processedBitmap = preprocessImage(image)
        val processImage = if (processedBitmap != null)
            InputImage.fromBitmap(processedBitmap, 0)
        else
            image

        return when (language) {
            TextRecognizerLanguage.AUTO -> recognizeAuto(processImage)
            else -> Tasks.await(getRecognizer(language).process(processImage))
        }
    }

    /**
     * Görüntü ön işleme:
     * 1. Küçük görüntüleri büyüt (OCR doğruluğu için min 1200px)
     * 2. Kontrast artır (metin/arka plan farkını belirginleştir)
     * 3. Hafif keskinleştirme
     */
    private fun preprocessImage(image: InputImage): Bitmap? {
        val bmp = image.bitmapInternal ?: return null
        val upscaleEnabled = prefs.ocrUpscaleEnabled().get()

        var result = bmp

        // 1. Upscale - min 1200px kısa kenar
        if (upscaleEnabled) {
            val minSide = minOf(result.width, result.height)
            if (minSide < 1200) {
                val scale = minOf(3f, 1200f / minSide)
                val matrix = Matrix().apply { postScale(scale, scale) }
                result = runCatching {
                    Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
                }.getOrDefault(result)
            }
        }

        // 2. Kontrast ve parlaklık artır — metin balonu arka planı net ayrışsın
        result = runCatching {
            val output = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply {
                // Kontrast: 1.3, Parlaklık: 10
                val contrast = 1.3f
                val brightness = 10f
                val cm = ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f,
                ))
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(result, 0f, 0f, paint)
            output
        }.getOrDefault(result)

        return if (result !== bmp) result else null
    }

    /**
     * AUTO: Latin önce dene (manga genellikle İngilizce).
     * 20+ karakter bulursa dur. Bulmadıysa Japonca > Çince > Korece.
     */
    private fun recognizeAuto(image: InputImage): Text {
        val latinResult = runCatching { Tasks.await(latinRecognizer.process(image)) }.getOrNull()
        val latinScore = latinResult?.textBlocks?.sumOf { it.text.length } ?: 0
        if (latinScore >= 20) return latinResult!!

        val candidates = listOf(
            japaneseRecognizer to 0,
            chineseRecognizer to 0,
            koreanRecognizer to 0,
        )
        var best = latinResult
        var bestScore = latinScore
        for ((rec, _) in candidates) {
            val result = runCatching { Tasks.await(rec.process(image)) }.getOrNull() ?: continue
            val score = result.textBlocks.sumOf { it.text.length }
            if (score > bestScore) { best = result; bestScore = score }
        }
        return best ?: Tasks.await(latinRecognizer.process(image))
    }

    private fun getRecognizer(lang: TextRecognizerLanguage): TextRecognizer = when (lang) {
        TextRecognizerLanguage.CHINESE -> chineseRecognizer
        TextRecognizerLanguage.JAPANESE -> japaneseRecognizer
        TextRecognizerLanguage.KOREAN -> koreanRecognizer
        else -> latinRecognizer
    }

    override fun close() {
        runCatching { latinRecognizer.close() }
        runCatching { chineseRecognizer.close() }
        runCatching { japaneseRecognizer.close() }
        runCatching { koreanRecognizer.close() }
    }
}
