package io.github.andreygladkov.ink.eink

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi

/**
 * Turns whatever a view draws into pure [ink] on a clear page, judging each pixel the way [Dither.luminance]
 * does: composited over white first, so see-through greys and anti-aliased edges also land on one side.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object MonochromeEffect {
    const val CONTENT = "content"

    private val AGSL = """
        uniform shader $CONTENT;
        layout(color) uniform half4 ink;

        half4 main(float2 xy) {
            half4 color = $CONTENT.eval(xy);
            half3 overWhite = color.rgb + (1.0 - color.a);
            half luma = dot(overWhite, half3(${Dither.RED / 1000.0}, ${Dither.GREEN / 1000.0}, ${Dither.BLUE / 1000.0}));
            return luma < ${Dither.THRESHOLD / 255.0} ? ink : half4(0.0);
        }
    """.trimIndent()

    private val effects = HashMap<Int, RenderEffect>()

    fun shader(@ColorInt ink: Int) = RuntimeShader(AGSL).apply { setColorUniform("ink", ink) }

    fun renderEffect(@ColorInt ink: Int): RenderEffect =
        synchronized(effects) { effects.getOrPut(ink) { RenderEffect.createRuntimeShaderEffect(shader(ink), CONTENT) } }
}
