package cn.sishiyuni.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import cn.sishiyuni.core.model.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Retain normalized pixels only; original location/device EXIF is not copied into the result. */
class ImageStore(private val context: Context) {
    suspend fun avatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri).use {
            requireNotNull(it) { "无法打开图片" }.readLimited(8 * 1024 * 1024)
        }
        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
        require(info.outWidth > 0 && info.outHeight > 0 && info.outWidth.toLong() * info.outHeight <= 40000000) {
            "图片尺寸过大或格式无效"
        }
        val sample = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (minOf(info.outWidth, info.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        val original = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sample)) { "无法解码图片" }
        val allocated = mutableSetOf<Bitmap>(original)
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
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            allocated.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
