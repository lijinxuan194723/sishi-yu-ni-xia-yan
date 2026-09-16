package cn.sishiyuni.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import cn.sishiyuni.core.model.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Only normalized pixels are retained. Source EXIF and original multi-megabyte files are not stored. */
class ImageStore(private val context:Context){
 suspend fun avatar(uri:Uri):String=withContext(Dispatchers.IO){
  val bytes=context.contentResolver.openInputStream(uri).use{requireNotNull(it){"无法打开图片"}.readLimited(8*1024*1024)}
  val info=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,info)
  require(info.outWidth>0&&info.outHeight>0&&info.outWidth.toLong()*info.outHeight<=40000000){"图片尺寸过大或格式无效"}
  val sample=BitmapFactory.Options().apply{inSampleSize=1;while(minOf(info.outWidth,info.outHeight)/inSampleSize>512)inSampleSize*=2}
  val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,sample)){"无法解码图片"}
  try{val side=minOf(bitmap.width,bitmap.height);val cropped=Bitmap.createBitmap(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side);val scaled=Bitmap.createScaledBitmap(cropped,256,256,true)
   val out=ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.PNG,100,out);if(scaled!==cropped)scaled.recycle();if(cropped!==bitmap)cropped.recycle();require(out.size()<=300000){"头像无法压缩到合适大小"};"data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
  }finally{bitmap.recycle()}
 }
}
