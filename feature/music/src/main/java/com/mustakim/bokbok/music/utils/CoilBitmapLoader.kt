 package com.mustakim.bokbok.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mustakim.bokbok.music.utils.reportException
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.delay
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlin.math.roundToInt

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                if (data.isEmpty()) {
                    throw IllegalArgumentException("Empty image data")
                }

                BitmapFactory.decodeByteArray(data, 0, data.size)?.also { bitmap ->
                    return@future bitmap
                }

                throw IllegalStateException("Could not decode image data")
            } catch (e: Exception) {
                reportException(e)
                return@future createBitmap(64, 64)
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            val density = context.resources.displayMetrics.density
            val maxIconSizePx = (density * 128f).roundToInt().coerceIn(128, 512)
            val attempts = 3
            for (attempt in 1..attempts) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .allowHardware(false)
                        .size(maxIconSizePx, maxIconSizePx)
                        .build()

                    val result = context.imageLoader.execute(request)

                    when (result) {
                        is SuccessResult -> {
                            try {
                                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                val scaled = if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                                    null
                                } else if (bitmap.width <= maxIconSizePx && bitmap.height <= maxIconSizePx) {
                                    bitmap
                                } else {
                                    val scale = minOf(
                                        maxIconSizePx.toFloat() / bitmap.width.toFloat(),
                                        maxIconSizePx.toFloat() / bitmap.height.toFloat(),
                                    )
                                    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                                    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                                    Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                                }

                                if (scaled == null) {
                                    return@future createBitmap(64, 64)
                                }

                                return@future scaled.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) {
                                reportException(e)
                                return@future createBitmap(64, 64)
                            }
                        }

                        is ErrorResult -> {
                            result.throwable?.let { reportException(it) }
                        }
                    }
                } catch (e: Exception) {
                    reportException(e)
                }

                if (attempt < attempts) {
                    delay(250L * attempt)
                    continue
                }
            }
            createBitmap(64, 64)
        }
}
