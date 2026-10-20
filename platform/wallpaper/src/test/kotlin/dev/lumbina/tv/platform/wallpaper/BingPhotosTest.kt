package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import org.junit.Assert.assertEquals
import org.junit.Test

class BingPhotosTest {

    private val answer =
        """{"images":[{"startdate":"20260926","url":"/th?id=OHR.BearsEars_ROW3487899068_1920x1080.jpg",""" +
            """"urlbase":"/th?id=OHR.BearsEars_ROW3487899068",""" +
            """"copyright":"Bears Ears, Utah (\u00a9 Jane \"JD\" Doe/Agency)","copyrightlink":"https://x"},""" +
            """{"urlbase" : "/th?id=OHR.Lake_EN-US123"}],"tooltips":{}}"""

    @Test
    fun `reads full HD photo URLs`() {
        assertEquals(
            listOf(
                "https://www.bing.com/th?id=OHR.BearsEars_ROW3487899068_1920x1080.jpg",
                "https://www.bing.com/th?id=OHR.Lake_EN-US123_1920x1080.jpg",
            ),
            bingPhotos(answer).map { it.url },
        )
    }

    @Test
    fun `each photo comes with the place and the author Bing names`() {
        assertEquals(
            listOf("Bears Ears, Utah (© Jane \"JD\" Doe/Agency)", null),
            bingPhotos(answer).map { it.credit },
        )
    }

    @Test
    fun `the credit is found wherever it stands in the photo's entry`() {
        val json = """{"images":[{"copyright":"A lake\/shore\n(\u00a9 A\\B)","hs":[],"urlbase":"/th?id=OHR.Lake"}]}"""
        assertEquals(
            listOf(DailyPhoto("https://www.bing.com/th?id=OHR.Lake_1920x1080.jpg", "A lake/shore (© A\\B)")),
            bingPhotos(json),
        )
    }

    @Test
    fun `an unexpected answer has no photos`() {
        assertEquals(emptyList<DailyPhoto>(), bingPhotos("<html>blocked</html>"))
    }
}
