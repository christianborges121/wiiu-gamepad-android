package com.cemupad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cemupad.config.DisplayFitMode
import com.cemupad.input.TouchInputHandler

/**
 * Interactive touch surface rendering the 16:9 GamePad screen area
 * and forwarding multi-touch gestures to TouchInputHandler.
 */
class TouchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var touchHandler: TouchInputHandler? = null
    var displayFitMode: DisplayFitMode = DisplayFitMode.ASPECT_FIT
        set(value) {
            field = value
            updateTouchViewport()
            postInvalidate()
        }
    var isVideoStreaming: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    private val borderPaint = Paint().apply {
        color = 0xFF00E5FF.toInt() // Vibrant Cyan
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = 0xFF121212.toInt()
        style = Paint.Style.FILL
    }

    private val pillarboxPaint = Paint().apply {
        color = 0xFF000000.toInt() // Solid black for aspect ratio letterboxing
        style = Paint.Style.FILL
    }

    private val activeAreaPaint = Paint().apply {
        color = 0xFF1E1E2E.toInt()
        style = Paint.Style.FILL
    }

    private val touchPaint = Paint().apply {
        color = 0xFFFFD700.toInt() // Golden yellow
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = 0xAAFFFFFF.toInt()
        textSize = 28f
        isAntiAlias = true
    }

    private var touch1X = -1f
    private var touch1Y = -1f
    private var touch2X = -1f
    private var touch2Y = -1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTouchViewport()
    }

    private fun updateTouchViewport() {
        val aspectRatio = when (displayFitMode) {
            DisplayFitMode.ASPECT_FIT, DisplayFitMode.FILL -> TouchInputHandler.TARGET_ASPECT_RATIO
            DisplayFitMode.STRETCH -> width.toFloat() / height.toFloat()
        }
        touchHandler?.updateDisplayDimensions(width.toFloat(), height.toFloat(), aspectRatio)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 0) {
                    touch1X = event.getX(0)
                    touch1Y = event.getY(0)
                }
                if (event.pointerCount > 1) {
                    touch2X = event.getX(1)
                    touch2Y = event.getY(1)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touch1X = -1f
                touch1Y = -1f
                touch2X = -1f
                touch2Y = -1f
                invalidate()
            }
        }

        val handled = touchHandler?.onTouchEvent(event) ?: false
        return handled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val targetAspect = when (displayFitMode) {
            DisplayFitMode.ASPECT_FIT, DisplayFitMode.FILL -> TouchInputHandler.TARGET_ASPECT_RATIO
            DisplayFitMode.STRETCH -> width.toFloat() / height.toFloat()
        }
        val activeW: Float
        val activeH: Float
        val offX: Float
        val offY: Float
        if (displayFitMode == DisplayFitMode.ASPECT_FIT) {
            activeW = (h * targetAspect).coerceAtMost(w)
            activeH = activeW / targetAspect
            offX = (w - activeW) / 2f
            offY = (h - activeH) / 2f
        } else {
            activeW = w
            activeH = h
            offX = 0f
            offY = 0f
        }

        if (isVideoStreaming) {
            // In video mode: Keep 16:9 active viewport transparent so the underlying SurfaceView is visible,
            // but mask out the pillarbox margins with solid black. No border is drawn while connected.
            if (offX > 0f) {
                // Left & Right pillarbox bars
                canvas.drawRect(0f, 0f, offX, h, pillarboxPaint)
                canvas.drawRect(offX + activeW, 0f, w, h, pillarboxPaint)
            }
            if (offY > 0f) {
                // Top & Bottom letterbox bars
                canvas.drawRect(0f, 0f, w, offY, pillarboxPaint)
                canvas.drawRect(0f, offY + activeH, w, h, pillarboxPaint)
            }
        } else {
            // Idle placeholder
            canvas.drawRect(0f, 0f, w, h, backgroundPaint)
            canvas.drawRect(offX, offY, offX + activeW, offY + activeH, activeAreaPaint)
            canvas.drawRect(offX, offY, offX + activeW, offY + activeH, borderPaint)

            // Labels pinned to the bottom of the active area: clear of both
            // the top-left diagnostics overlay and the centered startup card.
            val labelY = offY + activeH - 70f
            canvas.drawText("Wii U GamePad Touch Surface (16:9 Aspect-Fit)", offX + 24f, labelY, textPaint)
            canvas.drawText("Pillarbox margins outside cyan border are discarded", offX + 24f, labelY + 36f, textPaint)
        }

        // Draw touch indicator crosshairs
        if (touch1X >= 0 && touch1X in offX..(offX + activeW) && touch1Y in offY..(offY + activeH)) {
            canvas.drawCircle(touch1X, touch1Y, 32f, touchPaint)
            val normX = ((touch1X - offX) / activeW * TouchInputHandler.CEMU_TOUCH_MAX_X).toInt()
            val normY = ((touch1Y - offY) / activeH * TouchInputHandler.CEMU_TOUCH_MAX_Y).toInt()
            canvas.drawText("Touch 1: ($normX, $normY)", touch1X + 40f, touch1Y - 10f, textPaint)
        }

        if (touch2X >= 0 && touch2X in offX..(offX + activeW) && touch2Y in offY..(offY + activeH)) {
            canvas.drawCircle(touch2X, touch2Y, 32f, touchPaint)
            val normX = ((touch2X - offX) / activeW * TouchInputHandler.CEMU_TOUCH_MAX_X).toInt()
            val normY = ((touch2Y - offY) / activeH * TouchInputHandler.CEMU_TOUCH_MAX_Y).toInt()
            canvas.drawText("Touch 2: ($normX, $normY)", touch2X + 40f, touch2Y - 10f, textPaint)
        }
    }
}
