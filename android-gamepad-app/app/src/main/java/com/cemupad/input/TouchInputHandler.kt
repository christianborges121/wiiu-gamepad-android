package com.cemupad.input

import android.view.MotionEvent
import com.cemupad.dsu.DSUPacket
import com.cemupad.dsu.DSUServer
import kotlin.math.max

/**
 * Handles touchscreen events with 16:9 aspect-fit viewport calculation and coordinate
 * normalization to Cemu DSU's native 1920x942 coordinate space.
 */
class TouchInputHandler(
    private val dsuServer: DSUServer
) {
    companion object {
        const val TARGET_ASPECT_RATIO = 16.0f / 9.0f
        const val CEMU_TOUCH_MAX_X = 1919
        const val CEMU_TOUCH_MAX_Y = 941
    }

    data class Viewport(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val width: Float = 0f,
        val height: Float = 0f
    ) {
        fun contains(x: Float, y: Float): Boolean {
            return x in offsetX..(offsetX + width) && y in offsetY..(offsetY + height)
        }

        fun normalizeX(screenX: Float): Short {
            if (width <= 0f) return 0
            val u = ((screenX - offsetX) / width).coerceIn(0f, 1f)
            return (u * CEMU_TOUCH_MAX_X).toInt().toShort()
        }

        fun normalizeY(screenY: Float): Short {
            if (height <= 0f) return 0
            val v = ((screenY - offsetY) / height).coerceIn(0f, 1f)
            return (v * CEMU_TOUCH_MAX_Y).toInt().toShort()
        }
    }

    private var currentViewport = Viewport()
    private var touchPacketCounter: Byte = 0

    /**
     * Updates the active display dimensions to compute 16:9 pillarbox/letterbox margins.
     */
    fun updateDisplayDimensions(
        viewWidth: Float,
        viewHeight: Float,
        targetAspectRatio: Float = TARGET_ASPECT_RATIO
    ) {
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val screenAspect = viewWidth / viewHeight
        val (activeWidth, activeHeight, offsetX, offsetY) = if (screenAspect > targetAspectRatio) {
            // Screen is wider than the GamePad viewport -> pillarbox the sides.
            val activeW = viewHeight * targetAspectRatio
            val activeH = viewHeight
            val offX = (viewWidth - activeW) / 2.0f
            val offY = 0.0f
            Quadruple(activeW, activeH, offX, offY)
        } else {
            // Screen is taller than the GamePad viewport -> letterbox top and bottom.
            val activeW = viewWidth
            val activeH = viewWidth / targetAspectRatio
            val offX = 0.0f
            val offY = (viewHeight - activeH) / 2.0f
            Quadruple(activeW, activeH, offX, offY)
        }

        currentViewport = Viewport(
            offsetX = offsetX,
            offsetY = offsetY,
            width = activeWidth,
            height = activeHeight
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerCount = event.pointerCount
                var touch1Active = false
                var touch1X: Short = 0
                var touch1Y: Short = 0

                var touch2Active = false
                var touch2X: Short = 0
                var touch2Y: Short = 0

                if (pointerCount > 0) {
                    val x0 = event.getX(0)
                    val y0 = event.getY(0)
                    if (currentViewport.contains(x0, y0)) {
                        touch1Active = true
                        touch1X = currentViewport.normalizeX(x0)
                        touch1Y = currentViewport.normalizeY(y0)
                    }
                }

                if (pointerCount > 1) {
                    val x1 = event.getX(1)
                    val y1 = event.getY(1)
                    if (currentViewport.contains(x1, y1)) {
                        touch2Active = true
                        touch2X = currentViewport.normalizeX(x1)
                        touch2Y = currentViewport.normalizeY(y1)
                    }
                }

                touchPacketCounter = ((touchPacketCounter + 1) and 0xFF).toByte()

                val finalTouch1Active = touch1Active
                val finalTouch2Active = touch2Active

                dsuServer.updateState { state ->
                    state.touchButton = finalTouch1Active || finalTouch2Active
                    state.touch1 = DSUPacket.TouchPointData(
                        active = finalTouch1Active,
                        id = touchPacketCounter,
                        x = touch1X,
                        y = touch1Y
                    )
                    state.touch2 = DSUPacket.TouchPointData(
                        active = finalTouch2Active,
                        id = touchPacketCounter,
                        x = touch2X,
                        y = touch2Y
                    )
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.pointerCount > 1) {
                    // A secondary pointer lifted. Re-evaluate remaining pointers.
                    val liftedIndex = event.actionIndex
                    var touch1Active = false
                    var touch1X: Short = 0
                    var touch1Y: Short = 0

                    for (i in 0 until event.pointerCount) {
                        if (i == liftedIndex) continue
                        val x = event.getX(i)
                        val y = event.getY(i)
                        if (currentViewport.contains(x, y)) {
                            touch1Active = true
                            touch1X = currentViewport.normalizeX(x)
                            touch1Y = currentViewport.normalizeY(y)
                        }
                        break // Wii U GamePad single-touch primary tracking
                    }

                    touchPacketCounter = ((touchPacketCounter + 1) and 0xFF).toByte()
                    dsuServer.updateState { state ->
                        state.touchButton = touch1Active
                        state.touch1 = DSUPacket.TouchPointData(
                            active = touch1Active,
                            id = touchPacketCounter,
                            x = touch1X,
                            y = touch1Y
                        )
                        state.touch2 = DSUPacket.TouchPointData(active = false)
                    }
                } else {
                    // All touches released
                    dsuServer.updateState { state ->
                        state.touchButton = false
                        state.touch1 = DSUPacket.TouchPointData(active = false)
                        state.touch2 = DSUPacket.TouchPointData(active = false)
                    }
                }
                return true
            }
        }
        return false
    }

    private data class Quadruple(
        val width: Float,
        val height: Float,
        val offsetX: Float,
        val offsetY: Float
    )
}
