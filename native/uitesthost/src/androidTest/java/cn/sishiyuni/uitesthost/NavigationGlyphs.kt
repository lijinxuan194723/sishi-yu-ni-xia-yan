package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue

/** The CI Pixel 2 fixture uses three software-navigation buttons, not gesture mode. */
internal fun assertNightNavigationGlyphs(image: Bitmap, navigationHeight: Int) {
    assertTrue("Navigation inset must belong to the captured display",navigationHeight in 12..image.height/3)
    val top=image.height-navigationHeight
    repeat(3) { section ->
        var bright=0
        for(y in top until image.height) for(x in section*image.width/3 until (section+1)*image.width/3) {
            val pixel=image.getPixel(x,y)
            if(minOf(Color.red(pixel),Color.green(pixel),Color.blue(pixel))>150) bright++
        }
        assertTrue("Night navigation button $section lacks visible light glyph pixels: $bright",bright>=8)
    }
}
