package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun translateFromLanguage() = preferenceStore.getString("translate_language_from", "AUTO")
    fun translateToLanguage() = preferenceStore.getString("translate_language_to", "TURKISH")
    fun translationFont() = preferenceStore.getInt("translation_font", 0)
    fun translationEngine() = preferenceStore.getInt("translation_engine", 0)
    fun translationEngineModel() = preferenceStore.getString("translation_engine_model", "gemini-1.5-pro")
    fun translationEngineApiKey() = preferenceStore.getString("translation_engine_api_key", "")
    fun translationEngineTemperature() = preferenceStore.getString("translation_engine_temperature", "1")
    fun translationEngineMaxOutputTokens() = preferenceStore.getString("translation_engine_output_tokens", "8192")

    // Görüntüleme - String olarak sakla
    fun translationTextSize() = preferenceStore.getString("translation_text_size", "0") // "0" = otomatik
    fun translationBgMode() = preferenceStore.getInt("translation_bg_mode", 0) // 0=auto,1=white,2=black,3=none
    fun translationBgOpacity() = preferenceStore.getString("translation_bg_opacity", "0.92")
    fun translationTextColor() = preferenceStore.getInt("translation_text_color", 0) // 0=auto,1=black,2=white
    fun translationShowOriginal() = preferenceStore.getBoolean("translation_show_original", false)

    // Otomatik kaydırma - String olarak sakla
    fun autoScrollEnabled() = preferenceStore.getBoolean("auto_scroll_enabled", false)
    fun autoScrollInterval() = preferenceStore.getString("auto_scroll_interval", "5")
    fun autoScrollSpeed() = preferenceStore.getString("auto_scroll_speed", "2")

    // Google çeviri
    fun googleTranslateDelay() = preferenceStore.getString("google_translate_delay", "300")
    fun googleTranslateMaxRetry() = preferenceStore.getString("google_translate_max_retry", "3")

    // OCR
    fun ocrUpscaleEnabled() = preferenceStore.getBoolean("ocr_upscale_enabled", true)
}
