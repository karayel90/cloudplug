package tr.tabii

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.common.NextData

class TabiiChannelTest {

    private val mapper = ObjectMapper()

    private val trt1Json = """
        {
          "id": 150002,
          "slug": "trt1",
          "title": "TRT 1",
          "loginStatus": "watched_before_login",
          "images": [
            { "id": 23963, "images": [ { "key": "logo", "name": "trt1_logo.png" } ] }
          ],
          "media": [
            { "type": "hls",  "drmSchema": "clear",    "priority": 2, "url": "https://tv-trt1.medya.trt.com.tr/master.m3u8" },
            { "type": "dash", "drmSchema": "clear",    "priority": 2, "url": "https://tv-trt1-dash.medya.trt.com.tr/master.mpd" },
            { "type": "hls",  "drmSchema": "clear",    "priority": 1, "url": "https://trt.daioncdn.net/trt-1/master.m3u8?app=clean" }
          ]
        }
    """.trimIndent()

    private val tabiiTvJson = """
        {
          "id": 383911,
          "slug": "tabiitv",
          "title": "tabii TV",
          "loginStatus": "watched_after_login",
          "media": [
            { "type": "hls",  "drmSchema": "fairplay", "priority": 1, "url": "https://example.com/drm.m3u8" },
            { "type": "hls",  "drmSchema": "clear",    "priority": 2, "url": "https://ceokzokgtd.erbvr.com/tabiitv/tabiitv.m3u8" },
            { "type": "dash", "drmSchema": "widevine","priority": 1, "url": "https://example.com/drm.mpd" }
          ]
        }
    """.trimIndent()

    private fun parse(json: String) = mapper.readTree(json)

    @Test
    fun parsesChannel() {
        val channel = parse(trt1Json).toTabiiChannel()!!
        assertEquals(150002, channel.id)
        assertEquals("trt1", channel.slug)
        assertEquals("TRT 1", channel.title)
        // Taban adres verilmedigi icin logo uretilmiyor (kirik adres olusmaz).
        assertNull(channel.logo)
        assertEquals(3, channel.media.size)
        assertFalse(channel.requiresLogin)
        assertEquals("https://www.tabii.com/tr/watch/live/trt1?trackId=150002", channel.pageUrl)
    }

    @Test
    fun `logo taban adresiyle mutlak adrese donusur`() {
        // Sayfada gorseller yalnizca dosya adi olarak duruyor; mutlak adres
        // image_cdn_path ile olusuyor.
        val channel = parse(trt1Json).toTabiiChannel("https://cms-tabii-public-image.tabii.com/int")!!
        assertEquals(
            "https://cms-tabii-public-image.tabii.com/int/trt1_logo.png",
            channel.logo,
        )
    }

    @Test
    fun `taban adres sondaki boslukla da calisir`() {
        val channel = parse(trt1Json).toTabiiChannel("https://cdn.example.com/int/")!!
        assertEquals("https://cdn.example.com/int/trt1_logo.png", channel.logo)
    }

    @Test
    fun `taban adres bilinmiyorsa logo null`() {
        // Kirik adres yerine gorsel gosterilmez.
        val channel = parse(trt1Json).toTabiiChannel(null)!!
        assertNull(channel.logo)
    }

    @Test
    fun prefersParamlessClearHls() {
        val channel = parse(trt1Json).toTabiiChannel()!!
        // priority 1 olan daion adresi parametreli; parametresiz doğrudan CDN seçilir
        assertEquals(
            "https://tv-trt1.medya.trt.com.tr/master.m3u8",
            channel.playableMedia?.url,
        )
    }

    @Test
    fun skipsDrmMedia() {
        val channel = parse(tabiiTvJson).toTabiiChannel()!!
        assertTrue(channel.requiresLogin)
        val media = channel.playableMedia!!
        assertEquals("clear", media.drm)
        assertEquals("https://ceokzokgtd.erbvr.com/tabiitv/tabiitv.m3u8", media.url)
    }

    @Test
    fun returnsNullWhenNothingPlayable() {
        val json = """
            {"id":1,"slug":"x","title":"X","media":[
              {"type":"hls","drmSchema":"widevine","url":"https://e.com/a.m3u8"}
            ]}
        """.trimIndent()
        val channel = parse(json).toTabiiChannel()!!
        assertNull(channel.playableMedia)
    }

    @Test
    fun returnsNullForIncompleteChannel() {
        assertNull(parse("""{"slug":"trt1"}""").toTabiiChannel())
        assertNull(parse("""{"id":0,"slug":"trt1","title":"x"}""").toTabiiChannel())
        assertNull(parse("""{"id":1,"title":"x"}""").toTabiiChannel())
    }

    @Test
    fun readsChannelsFromNextData() {
        val html = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"liveChannels":[$trt1Json,$tabiiTvJson]}},"query":{"trackId":"150002"}}
            </script></head></html>
        """.trimIndent()
        val tree = NextData.tree(html)!!
        val array = NextData.node(tree, "props", "pageProps", "liveChannels")!!
        val channels = array.mapNotNull { it.toTabiiChannel() }
        assertEquals(2, channels.size)
        assertEquals(listOf("TRT 1", "tabii TV"), channels.map { it.title })
    }
}
