package eu.kanade.translation.presentation

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import eu.kanade.translation.data.TranslationFont
import tachiyomi.domain.translation.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.translation.model.PageTranslation
import kotlinx.coroutines.flow.MutableStateFlow

class PagerTranslationsView :
    AbstractComposeView {

    private val translation: PageTranslation
    private val font: TranslationFont
    private val fontFamily: FontFamily

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        this.translation = PageTranslation.EMPTY
        this.font = TranslationFont.ANIME_ACE
        this.fontFamily = Font(
            resId = font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
    }

    private var bgMode: Int = 0
    private var textColorMode: Int = 0
    private var bgOpacity: Float = 0.92f
    private var fixedTextSize: Float = 0f

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
        this.fontFamily = Font(
            resId = this.font.res,
            weight = FontWeight.Bold,
        ).toFontFamily()
        this.bgMode = prefs.translationBgMode().get()
        this.textColorMode = prefs.translationTextColor().get()
        this.bgOpacity = prefs.translationBgOpacity().get().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.92f
        this.fixedTextSize = prefs.translationTextSize().get().toFloatOrNull() ?: 0f
    }

    val scaleState = MutableStateFlow(1f)
    val viewTLState = MutableStateFlow(PointF())

    @Composable
    override fun Content() {
        val viewTL by viewTLState.collectAsState()
        val scale by scaleState.collectAsState()
        Box(
            modifier = Modifier
                .absoluteOffset(viewTL.x.pxToDp(), viewTL.y.pxToDp()),
        ) {
            TextBlockBackground(scale)
            TextBlockContent(scale)
        }
    }

    @Composable
    fun TextBlockBackground(zoomScale: Float) {
        translation.blocks.forEach { block ->
            val padX = block.symWidth / 2
            val padY = block.symHeight / 2
            val bgX = ((block.x - padX / 2) * 1) * zoomScale
            val bgY = ((block.y - padY / 2) * 1) * zoomScale
            val bgWidth = (block.width + padX) * zoomScale
            val bgHeight = (block.height + padY) * zoomScale
            val isVertical = block.angle > 85
            val bgColor = when (bgMode) {
                1 -> Color.White.copy(alpha = bgOpacity)
                2 -> Color.Black.copy(alpha = bgOpacity)
                3 -> Color.Transparent
                else -> {
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
                        .wrapContentSize(Alignment.CenterStart, true)
                        .offset(bgX.pxToDp(), bgY.pxToDp())
                        .requiredSize(bgWidth.pxToDp(), bgHeight.pxToDp())
                        .rotate(if (isVertical) 0f else block.angle)
                        .background(bgColor, shape = RoundedCornerShape(4.dp)),
                )
            }
        }
    }

    @Composable
    fun TextBlockContent(zoomScale: Float) {
        translation.blocks.forEach { block ->
            val textColor = when (textColorMode) {
                1 -> Color.Black
                2 -> Color.White
                else -> {
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
                scaleFactor = zoomScale,
                fontFamily = fontFamily,
                textColor = textColor,
                fixedTextSizeSp = fixedTextSize,
            )
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}
