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
                else fromLang.code
            )
            .setTargetLanguage(
                TranslateLanguage.fromLanguageTag(toLang.code) ?: TranslateLanguage.ENGLISH
            )
            .build(),
    )

    override suspend fun translate(pages: MutableMap<String, PageTranslation>) {
        Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
        pages.values.forEach { page ->
            page.blocks.forEach { block ->
                // \n sorununu çöz: her satırı ayrı çevir
                val lines = block.text.split("\n").filter { it.isNotBlank() }
                val translatedLines = lines.map { line ->
                    try {
                        Tasks.await(translator.translate(line.trim()))
                            .takeIf { it.isNotBlank() } ?: line
                    } catch (e: Exception) {
                        line
                    }
                }
                block.translation = translatedLines.joinToString(" ")
            }
        }
    }

    override fun close() {
        translator.close()
    }
}
