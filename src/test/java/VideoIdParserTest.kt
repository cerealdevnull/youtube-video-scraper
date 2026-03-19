import com.cereal.script.sample.VideoIdParser
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoIdParserTest {

    @Test
    fun `parses standard watch URL`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `parses short youtu_be URL`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `parses short youtu_be URL with query params`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
    }

    @Test
    fun `parses raw video ID`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("dQw4w9WgXcQ"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(VideoIdParser.parse(""))
    }

    @Test
    fun `returns null for whitespace-only input`() {
        assertNull(VideoIdParser.parse("   "))
    }

    @Test
    fun `returns null for unrecognised URL`() {
        assertNull(VideoIdParser.parse("https://example.com/video/123"))
    }

    @Test
    fun `parses watch URL with extra query params`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&list=PL123"))
    }
}
