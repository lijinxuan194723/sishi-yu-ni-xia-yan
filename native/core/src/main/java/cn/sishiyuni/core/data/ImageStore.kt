package cn.sishiyuni.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import cn.sishiyuni.core.model.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/** Retain normalized pixels only; original location/device EXIF is never copied. */
class ImageStore(private val context: Context) {
    suspend fun avatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri).use {
            requireNotNull(it) { "无法打开图片" }.readLimited(8 * 1024 * 1024)
        }
        coroutineContext.ensureActive()
        AvatarPixels.normalize(bytes).also { coroutineContext.ensureActive() }
    }
}

object AvatarPixels {
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0 && maxSide > 0)
        var sample = 1
        val largest = maxOf(width, height).toLong()
        while ((largest + sample - 1) / sample > maxSide) sample *= 2
        return sample
    }

    fun normalize(bytes: ByteArray): String {
        require(bytes.size <= 8 * 1024 * 1024) { "图片文件过大" }
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
        require(info.outWidth > 0 && info.outHeight > 0 && info.outWidth.toLong() * info.outHeight <= 40000000) {
            "图片尺寸过大或格式无效"
        }
        // Decode only the center square. Sampling by the *short* side of a panorama
        // used to decode the entire long image before throwing most pixels away.
        val original = decodeCenter(bytes, info.outWidth, info.outHeight) ?: run {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(info.outWidth, info.outHeight, 1024)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "无法解码图片" }
        }
        val allocated = mutableSetOf(original)
        try {
            val orientation = runCatching {
                ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also { allocated += it }
            val side = minOf(oriented.width, oriented.height)
            val cropped = Bitmap.createBitmap(oriented, (oriented.width - side) / 2, (oriented.height - side) / 2, side, side).also { allocated += it }
            val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true).also { allocated += it }
            val out = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) { "头像编码失败" }
            require(out.size() <= 300000) { "头像无法压缩到合适大小" }
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            allocated.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeCenter(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val decoder = try { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) }
            catch (_: java.io.IOException) { return null }
        if (decoder == null) return null
        return try {
            val side = minOf(width, height)
            val left = (width - side) / 2
            val top = (height - side) / 2
            decoder.decodeRegion(Rect(left, top, left + side, top + side), BitmapFactory.Options().apply {
                inSampleSize = sampleSize(side, side, 512)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (_: IllegalArgumentException) { null }
        finally { decoder.recycle() }
    }
}
