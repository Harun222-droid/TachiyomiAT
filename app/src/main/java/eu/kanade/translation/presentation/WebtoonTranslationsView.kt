package eu.kanade.translation.presentation

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import eu.kanade.translation.model.PageTranslation
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WebtoonTranslationsView : AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily
    private val bgMode: Int
    private val textColorMode: Int
    private val bgOpacity: Float
    private val fixedTextSize: Float

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = PageTranslation.EMPTY
        this.font = TranslationFont.ANIME_ACE
        this.fontFamily = Font(resId = font.res, weight = FontWeight.Bold).toFontFamily()
        this.bgMode = 0
        this.textColorMode = 0
        this.bgOpacity = 0.92f
        this.fixedTextSize = 0f
    }

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        translation: PageTranslation,
        font: TranslationFont? = null,
        prefs: TranslationPreferences = Injekt.get(),
    ) : super(context, attrs, defStyleAttr) {
        this.translation = translation
        this.font = font ?: TranslationFont.ANIME_ACE
        this.fontFamily = Font(resId = this.font.res, weight = FontWeight.Bold).toFontFamily()
        this.bgMode = prefs.translationBgMode().get()
        this.textColorMode = prefs.translationTextColor().get()
        this.bgOpacity = prefs.translationBgOpacity().get().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.92f
        this.fixedTextSize = prefs.translationTextSize().get().toFloatOrNull() ?: 0f
    }

    @Composable
    override fun Content() {
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (size == IntSize.Zero) hide() else show()
                },
        ) {
            if (size == IntSize.Zero) return
            val scaleFactor = size.width / translation.imgWidth
            TextBlockBackground(scaleFactor)
            TextBlockContent(scaleFactor)
        }
    }

    @Composable
    fun TextBlockBackground(scaleFactor: Float) {
        translation.blocks.forEach { block ->
            val padX = block.symWidth / 2
            val padY = block.symHeight / 2
            val bgX = (block.x - padX / 2) * scaleFactor
            val bgY = (block.y - padY / 2) * scaleFactor
            val bgWidth = (block.width + padX) * scaleFactor
            val bgHeight = (block.height + padY) * scaleFactor
            val isVertical = block.angle > 85

            // Arka plan rengi: ayarlara göre seç
            val bgColor = when (bgMode) {
                1 -> Color.White.copy(alpha = bgOpacity) // Beyaz
                2 -> Color.Black.copy(alpha = bgOpacity) // Siyah
                3 -> Color.Transparent                   // Yok
                else -> {
                    // Auto: bloğun kendi arka plan rengi
                    val argb = block.bgColorArgb
                    Color(
                        red = ((argb shr 16) and 0xFF) / 255f,
                        green = ((argb shr 8) and 0xFF) / 255f,
                        blue = (argb and 0xFF) / 255f,
                        alpha = bgOpacity,
                    )
                }
            }

            if (bgMode != 3) {
                Box(
                    modifier = Modifier
                        .offset(bgX.pxToDp(), bgY.pxToDp())
                        .size(bgWidth.pxToDp(), bgHeight.pxToDp())
                        .rotate(if (isVertical) 0f else block.angle)
                        .background(bgColor, shape = RoundedCornerShape(4.dp)),
                )
            }
        }
    }

    @Composable
    fun TextBlockContent(scaleFactor: Float) {
        translation.blocks.forEach { block ->
            // Metin rengi: ayarlara göre seç
            val textColor = when (textColorMode) {
                1 -> Color.Black
                2 -> Color.White
                else -> {
                    // Auto: bgColor'a göre kontrast
                    val argb = block.textColorArgb
                    Color(
                        red = ((argb shr 16) and 0xFF) / 255f,
                        green = ((argb shr 8) and 0xFF) / 255f,
                        blue = (argb and 0xFF) / 255f,
                    )
                }
            }
            SmartTranslationBlock(
                block = block,
                scaleFactor = scaleFactor,
                fontFamily = fontFamily,
                textColor = textColor,
                fixedTextSizeSp = fixedTextSize,
            )
        }
    }

    fun show() { isVisible = true }
    fun hide() { isVisible = false }
}
