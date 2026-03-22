package eu.kanade.translation.model

import kotlinx.serialization.Serializable

@Serializable
data class PageTranslation(
    var blocks: MutableList<TranslationBlock> = mutableListOf(),
    var imgWidth: Float = 0f,
    var imgHeight: Float = 0f,
) {
    companion object {
        val EMPTY = PageTranslation()
    }
}

@Serializable
data class TranslationBlock(
    var text: String,
    var translation: String = "",
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    var symHeight: Float,
    var symWidth: Float,
    val angle: Float,
    // Arka plan rengi (ARGB int) - OCR sırasında doldurulur
    var bgColorArgb: Int = 0xFFFFFFFF.toInt(),
    // Metin rengi - bgColor'a göre otomatik hesaplanır
    var textColorArgb: Int = 0xFF000000.toInt(),
)
