package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage
import eu.kanade.translation.translator.TextTranslators
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.at.ATMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ATMR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val p = remember { Injekt.get<TranslationPreferences>() }
        return listOf(
            Preference.PreferenceItem.ListPreference(
                pref = p.translationMode(),
                title = stringResource(ATMR.strings.pref_translation_mode),
                entries = mapOf(
                    0 to "Sadece indirilen bölümler",
                    1 to "Canlı (okurken çevir)",
                    2 to "Her ikisi (canlı + indirilmiş)",
                ).toImmutableMap(),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = p.autoTranslateAfterDownload(),
                title = stringResource(ATMR.strings.pref_translate_after_downloading),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = p.translationFont(),
                title = stringResource(ATMR.strings.pref_reader_font),
                entries = TranslationFont.entries.withIndex()
                    .associate { it.index to it.value.label }.toImmutableMap(),
            ),
            getLangGroup(p),
            getEngineGroup(p),
            getDisplayGroup(p),
            getAutoScrollGroup(p),
            getOcrGroup(p),
            getGoogleGroup(p),
            getAdvancedGroup(p),
        )
    }

    @Composable
    private fun getLangGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_setup),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.ListPreference(
                pref = p.translateFromLanguage(),
                title = stringResource(ATMR.strings.pref_translate_from),
                entries = TextRecognizerLanguage.entries
                    .associate { it.name to it.label }.toImmutableMap(),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = p.translateToLanguage(),
                title = stringResource(ATMR.strings.pref_translate_to),
                entries = TextTranslatorLanguage.entries
                    .associate { it.name to it.label }.toImmutableMap(),
            ),
        ),
    )

    @Composable
    private fun getEngineGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_engine),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.ListPreference(
                pref = p.translationEngine(),
                title = stringResource(ATMR.strings.pref_translator_engine),
                entries = TextTranslators.entries.withIndex()
                    .associate { it.index to it.value.label }.toImmutableMap(),
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationEngineApiKey(),
                title = stringResource(ATMR.strings.pref_engine_api_key),
                subtitle = stringResource(ATMR.strings.pref_sub_engine_api_key),
            ),
        ),
    )

    @Composable
    private fun getDisplayGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_display),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.ListPreference(
                pref = p.translationBgMode(),
                title = stringResource(ATMR.strings.pref_bg_mode),
                entries = mapOf(
                    0 to "Otomatik (sayfa rengi)",
                    1 to "Beyaz",
                    2 to "Siyah",
                    3 to "Yok (saydam)",
                ).toImmutableMap(),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = p.translationTextColor(),
                title = stringResource(ATMR.strings.pref_text_color),
                entries = mapOf(
                    0 to "Otomatik",
                    1 to "Siyah",
                    2 to "Beyaz",
                ).toImmutableMap(),
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationBgOpacity(),
                title = stringResource(ATMR.strings.pref_bg_opacity),
                subtitle = "0.0 ile 1.0 arası (varsayılan: 0.92)",
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationTextSize(),
                title = stringResource(ATMR.strings.pref_text_size),
                subtitle = "0 = otomatik, ya da sabit boyut girin (örn: 14)",
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = p.translationShowOriginal(),
                title = stringResource(ATMR.strings.pref_show_original),
            ),
        ),
    )

    @Composable
    private fun getAutoScrollGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_autoscroll),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = p.autoScrollEnabled(),
                title = stringResource(ATMR.strings.pref_autoscroll_enabled),
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.autoScrollInterval(),
                title = stringResource(ATMR.strings.pref_autoscroll_interval),
                subtitle = "Saniye/sayfa (varsayılan: 5)",
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.autoScrollSpeed(),
                title = stringResource(ATMR.strings.pref_autoscroll_speed),
                subtitle = "Kaydırma hızı px/kare (varsayılan: 2)",
            ),
        ),
    )

    @Composable
    private fun getOcrGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_ocr),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = p.ocrUpscaleEnabled(),
                title = stringResource(ATMR.strings.pref_ocr_upscale),
                subtitle = stringResource(ATMR.strings.pref_ocr_upscale_sub),
            ),
        ),
    )

    @Composable
    private fun getGoogleGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_google),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.EditTextPreference(
                pref = p.googleTranslateDelay(),
                title = stringResource(ATMR.strings.pref_google_delay),
                subtitle = "Banlamamak için istek arası bekleme ms (varsayılan: 300)",
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.googleTranslateMaxRetry(),
                title = stringResource(ATMR.strings.pref_google_retry),
                subtitle = "Hata durumunda tekrar sayısı (varsayılan: 3)",
            ),
        ),
    )

    @Composable
    private fun getAdvancedGroup(p: TranslationPreferences) = Preference.PreferenceGroup(
        title = stringResource(ATMR.strings.pref_group_advanced),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationEngineModel(),
                title = stringResource(ATMR.strings.pref_engine_model),
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationEngineTemperature(),
                title = stringResource(ATMR.strings.pref_engine_temperature),
            ),
            Preference.PreferenceItem.EditTextPreference(
                pref = p.translationEngineMaxOutputTokens(),
                title = stringResource(ATMR.strings.pref_engine_max_output),
            ),
        ),
    )
}
