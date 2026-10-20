package tr.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextDataTest {

    private val html = """
        <!DOCTYPE html><html><head>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"liveChannels":[{"id":150002,"title":"TRT 1"}]},"query":{"trackId":"150002"}},
         "page":"/watch/live/[[...slug]]","buildId":"24.09.2026-04.30.04"}
        </script></head><body></body></html>
    """.trimIndent()

    @Test
    fun extractsRawJson() {
        val raw = NextData.extract(html)
        assertTrue(raw != null)
        assertTrue(raw!!.contains("liveChannels"))
    }

    @Test
    fun parsesTree() {
        val tree = NextData.tree(html)
        assertTrue(tree != null)
        val title = NextData.node(tree, "props", "pageProps", "liveChannels")?.get(0)?.get("title")?.asText()
        assertEquals("TRT 1", title)
    }

    @Test
    fun returnsNullWhenBlockMissing() {
        assertNull(NextData.extract("<html><body>hicbir sey</body></html>"))
        assertNull(NextData.tree("<html><body>hicbir sey</body></html>"))
    }

    @Test
    fun returnsNullOnBrokenJson() {
        val broken = """<script id="__NEXT_DATA__" type="application/json">{bozuk json</script>"""
        assertNull(NextData.tree(broken))
    }

    @Test
    fun missingPathYieldsNull() {
        val tree = NextData.tree(html)
        assertNull(NextData.node(tree, "props", "yok", "bu da yok"))
        assertNull(NextData.node(null, "props"))
    }

    @Test
    fun findsNestedImageByKey() {
        val tree = NextData.tree(
            """<script id="__NEXT_DATA__" type="application/json">
            {"images":[{"id":1,"images":[{"key":"logo","name":"trt1_logo.png"}]}]}
            </script>"""
        )
        val found = NextData.findByKey(tree, "key", "logo")
        assertEquals("trt1_logo.png", found?.get("name")?.asText())
    }
}
