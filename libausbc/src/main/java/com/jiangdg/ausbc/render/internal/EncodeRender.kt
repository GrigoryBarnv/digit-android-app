/*
 * Copyright 2017-2023 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.EGLContext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.env.EGLEvn
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Inherit from AbstractFboRender
 *      render data to EGL from fbo and encode it
 *
 * @author Created by jiangdg on 2021/12/27
 */
class EncodeRender(context: Context): AbstractRender(context) {

    private var mEgl: EGLEvn? = null
    private var overlayText: String? = null
    private var uploadedOverlayText: String? = null
    private var overlayTextureId: Int = 0
    private var overlayBitmapWidth: Int = 0
    private var overlayBitmapHeight: Int = 0
    private val overlayVertices: FloatBuffer = ByteBuffer.allocateDirect(4 * 5 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun initEGLEvn(glContext: EGLContext) {
        mEgl = EGLEvn()
        mEgl?.initEgl(glContext)
    }

    fun setupSurface(surface: Surface) {
        mEgl?.setupSurface(surface)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    fun setOverlayText(text: String?) {
        overlayText = text?.takeIf { it.isNotBlank() }
    }

    override fun drawFrame(textureId: Int): Int {
        super.drawFrame(textureId)
        drawOverlayIfNeeded()
        return textureId
    }

    override fun clear() {
        if (overlayTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTextureId), 0)
            overlayTextureId = 0
        }
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment

    private fun drawOverlayIfNeeded() {
        val text = overlayText ?: return
        if (mWidth <= 0 || mHeight <= 0) return
        ensureOverlayTexture(text)
        if (overlayTextureId == 0 || overlayBitmapWidth <= 0 || overlayBitmapHeight <= 0) return

        val minSide = minOf(mWidth, mHeight).toFloat()
        val margin = maxOf(minSide * 0.035f, 8f)
        val left = margin
        val bottom = mHeight.toFloat() - margin
        val top = bottom - overlayBitmapHeight
        val right = (left + overlayBitmapWidth).coerceAtMost(mWidth.toFloat() - margin)
        if (right <= left || bottom <= top) return

        val x0 = -1f + 2f * left / mWidth.toFloat()
        val x1 = -1f + 2f * right / mWidth.toFloat()
        val y0 = 1f - 2f * bottom / mHeight.toFloat()
        val y1 = 1f - 2f * top / mHeight.toFloat()

        overlayVertices.clear()
        overlayVertices.put(
            floatArrayOf(
                x0, y0, 0f, 0f, 1f,
                x1, y0, 0f, 1f, 1f,
                x0, y1, 0f, 0f, 0f,
                x1, y1, 0f, 1f, 0f,
            )
        )
        overlayVertices.position(0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(mProgram)

        overlayVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            mPositionLocation,
            3,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            overlayVertices
        )
        GLES20.glEnableVertexAttribArray(mPositionLocation)
        overlayVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            mTextureCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            overlayVertices
        )
        GLES20.glEnableVertexAttribArray(mTextureCoordLocation)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(mTextureSampler, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun ensureOverlayTexture(text: String) {
        if (uploadedOverlayText == text && overlayTextureId != 0) return
        uploadedOverlayText = text

        val bitmap = createOverlayBitmap(text)
        overlayBitmapWidth = bitmap.width
        overlayBitmapHeight = bitmap.height

        if (overlayTextureId == 0) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            overlayTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
    }

    private fun createOverlayBitmap(text: String): Bitmap {
        val minSide = minOf(mWidth, mHeight).toFloat().coerceAtLeast(1f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = minOf(maxOf(minSide * 0.045f, 11f), 18f)
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val padding = maxOf(minSide * 0.025f, 6f)
        val margin = maxOf(minSide * 0.035f, 8f)
        val maxTextWidth = mWidth.toFloat() - margin * 2f - padding * 2f
        val visibleChars = textPaint.breakText(text, true, maxTextWidth, null)
            .coerceIn(0, text.length)
        val overlayText = if (visibleChars < text.length && visibleChars > 1) {
            "${text.take(visibleChars - 1)}…"
        } else {
            text
        }
        val textWidth = textPaint.measureText(overlayText)
        val textHeight = textPaint.fontMetrics.run { bottom - top }
        val bitmapWidth = (textWidth + padding * 2f).toInt().coerceAtLeast(1)
        val bitmapHeight = (textHeight + padding * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 32, 33, 38)
        }
        val rect = RectF(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat())
        canvas.drawRoundRect(rect, padding, padding, backgroundPaint)
        canvas.drawText(
            overlayText,
            padding,
            bitmapHeight - padding - textPaint.fontMetrics.bottom,
            textPaint
        )
        return bitmap
    }
}
