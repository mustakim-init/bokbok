package com.mustakim.bokbok.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Edge Glow overlay that creates a pulsing gradient effect at screen edges
 * when voice mode is active. Mimics Google Assistant's edge glow.
 */
class EdgeGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 180
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    private var breathAnimValue = 0f
    private var breathAnimator: ValueAnimator? = null
    
    private val neonBlue = Color.parseColor("#00F0FF") 
    private val neonPurple = Color.parseColor("#BC13FE")
    
    private var currentAmplitude = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startBreathingAnimation()
    }

    private fun startBreathingAnimation() {
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000 // Slower breathing
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                breathAnimValue = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setAmplitude(amplitude: Float) {
        // High sensitivity for normal speech
        val target = amplitude.coerceIn(0f, 1f)
        currentAmplitude = currentAmplitude * 0.8f + target * 0.2f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Base state: faint life
        // Active state: steady glow (no blinking)
        val activeIntensity = currentAmplitude * 1.5f 
        val totalIntensity = (0.3f + (breathAnimValue * 0.1f) + activeIntensity).coerceIn(0f, 1f)

        // Thinner profile as requested (Max 60px)
        val glowWidth = 15f + (totalIntensity * 45f) 
        val alpha = (totalIntensity * 230).toInt()

        glowPaint.strokeWidth = glowWidth
        glowPaint.style = Paint.Style.STROKE
        glowPaint.shader = LinearGradient(0f, 0f, w, h, neonBlue, neonPurple, Shader.TileMode.CLAMP)
        glowPaint.alpha = alpha

        // Use PorterDuff ADD for "glowy" bloom effect if possible on this canvas
        // For simplicity with BlurMaskFilter, we'll just layer it.
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // Inner core for neon look
        if (totalIntensity > 0.4f) {
            corePaint.strokeWidth = glowWidth * 0.25f
            corePaint.style = Paint.Style.STROKE
            corePaint.alpha = (alpha * 0.6f).toInt()
            canvas.drawRect(0f, 0f, w, h, corePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator?.cancel()
    }
}
