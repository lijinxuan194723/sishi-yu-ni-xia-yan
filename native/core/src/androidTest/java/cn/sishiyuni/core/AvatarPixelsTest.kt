package cn.sishiyuni.core

import android.graphics.*
import android.media.ExifInterface
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.AvatarPixels
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AvatarPixelsTest {
    private fun encoded(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray =
        ByteArrayOutputStream().use { out -> assertTrue(bitmap.compress(format, 100, out)); out.toByteArray() }
    private fun output(bytes: ByteArray): Bitmap {
        val result = AvatarPixels.normalize(bytes)
        assertTrue(result.startsWith("data:image/png;base64,"))
        val decoded = Base64.decode(result.substringAfter(','), Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(decoded, 0, decoded.size)!!
    }
    @Test fun transparentCutoutStaysTransparentAndHasNoAddedBackdrop() {
        val source = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        try {
            Canvas(source).drawCircle(32f,32f,16f,Paint().apply { color=Color.RED })
            val result=output(encoded(source))
            try {
                assertEquals(256,result.width);assertEquals(256,result.height)
                assertEquals(0,Color.alpha(result.getPixel(0,0)))
                assertEquals(Color.RED,result.getPixel(128,128))
            } finally { result.recycle() }
        } finally { source.recycle() }
    }
    @Test fun panoramicCenterIsCroppedBeforeExpensiveFullImageAllocation() {
        val source=Bitmap.createBitmap(6000,96,Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.BLUE)
            Canvas(source).drawRect(2952f,0f,3048f,96f,Paint().apply{color=Color.GREEN})
            val result=output(encoded(source))
            try { assertEquals(Color.GREEN,result.getPixel(16,16));assertEquals(Color.GREEN,result.getPixel(240,240)) }
            finally { result.recycle() }
        } finally { source.recycle() }
    }
    @Test fun allEightExifOrientationsHaveTheCorrectVisibleCorners() {
        val source=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(source)
        val colors=listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW)
        for(i in colors.indices) canvas.drawRect((i%2)*40f,(i/2)*40f,(i%2+1)*40f,(i/2+1)*40f,Paint().apply{color=colors[i]})
        val orders=mapOf(1 to listOf(0,1,2,3),2 to listOf(1,0,3,2),3 to listOf(3,2,1,0),4 to listOf(2,3,0,1),
            5 to listOf(0,2,1,3),6 to listOf(2,0,3,1),7 to listOf(3,1,2,0),8 to listOf(1,3,0,2))
        val cache=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        try {
            for((orientation,order) in orders) {
                val file=File(cache,"orientation-${UUID.randomUUID()}.jpg")
                try {
                    file.writeBytes(encoded(source,Bitmap.CompressFormat.JPEG))
                    ExifInterface(file.path).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION,orientation.toString())
                        setAttribute(ExifInterface.TAG_MAKE,"PrivateFixtureCamera")
                        saveAttributes()
                    }
                    val text=AvatarPixels.normalize(file.readBytes())
                    val bytes=Base64.decode(text.substringAfter(','),Base64.NO_WRAP)
                    val result=BitmapFactory.decodeByteArray(bytes,0,bytes.size)!!
                    try {
                        for(i in 0..3) {
                            val actual=result.getPixel(if(i%2==0)32 else 224,if(i<2)32 else 224)
                            val expected=colors[order[i]]
                            assertTrue("orientation=$orientation corner=$i",kotlin.math.abs(Color.red(actual)-Color.red(expected))<10 &&
                                kotlin.math.abs(Color.green(actual)-Color.green(expected))<10 && kotlin.math.abs(Color.blue(actual)-Color.blue(expected))<10)
                        }
                        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("PrivateFixtureCamera"))
                    } finally { result.recycle() }
                } finally { file.delete() }
            }
        } finally { source.recycle() }
    }
    @Test fun invalidOrOversizedEncodedInputDoesNotProduceAnAvatar() {
        assertThrows(IllegalArgumentException::class.java){AvatarPixels.normalize("not an image".toByteArray())}
        assertThrows(IllegalArgumentException::class.java){AvatarPixels.normalize(ByteArray(8*1024*1024+1))}
    }
    @Test fun fallbackSamplingAlwaysBoundsTheLongestDecodedSide() {
        for((w,h) in listOf(1 to 40000000,6000 to 96,8000 to 5000,512 to 512,513 to 513)) {
            val sample=AvatarPixels.sampleSize(w,h,1024)
            assertTrue((maxOf(w,h).toLong()+sample-1)/sample<=1024)
            assertTrue(sample>0 && sample and (sample-1)==0)
        }
    }
}
