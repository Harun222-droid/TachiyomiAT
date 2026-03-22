package eu.kanade.translation.translator

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import eu.kanade.translation.model.PageTranslation
import eu.kanade.translation.recognizer.TextRecognizerLanguage

class MLKitTranslator(
    override val fromLang: TextRecognizerLanguage,
    override val toLang: TextTranslatorLanguage,
) : TextTranslator {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(
                if (fromLang == TextRecognizerLanguage.AUTO) TranslateLanguage.ENGLISH
                else fromLang.code,
            )
            .setTargetLanguage(
                TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.TURKISH,
            )
            .build(),
    )

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
        pages.values.forEach { page ->
            page.blocks.forEach { block ->
                try {
                    // OCR karışık harf sorununu düzelt: normalize et
                    val normalized = normalizeText(block.text)
                    // Tüm satırları birleştir - bağlam korunur
                    val fullText = normalized.replace("\n", " ").trim()
                    if (fullText.isBlank()) return@forEach
                    val translated = Tasks.await(translator.translate(fullText))
                    block.translation = if (translated.isNotBlank()) translated else fullText
                } catch (e: Exception) {
                    block.translation = block.text.replace("\n", " ").trim()
                }
            }
        }
    }

    /**
     * OCR bazen karışık büyük/küçük harf okur: "wELL" → "WELL", "peINCE" → "PRINCE"
     * Tüm metni büyük harfe çevir — manga metinleri zaten büyük harf.
     */
    private fun normalizeText(text: String): String {
        return text.uppercase()
            .replace(Regex("[^A-Z0-9\\s\\p{Punct}]"), "") // Garip karakterleri temizle
            .trim()
            .ifBlank { text } // Boş kalırsa orijinali döndür
    }

    override fun close() {
        translator.close()
    }
}
