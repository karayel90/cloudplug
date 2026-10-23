package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.common.M3u

class TrLiveChannelsTest {

    @Test
    fun `liste adresi topluluk m3u kaynakli olmali`() {
        assertTrue(TrLiveChannels.PLAYLIST_URL.startsWith("https://raw.githubusercontent.com/"))
        assertTrue(TrLiveChannels.PLAYLIST_URL.endsWith(".m3u"))
    }

    @Test
    fun `daion kanallari kendi sitelerini referer olarak alir`() {
        val expected = mapOf(
            "https://tv8.daioncdn.net/tv8/tv8.m3u8?app=abc&ce=3" to "https://www.tv8.com.tr/",
            "https://dogus.daioncdn.net/startv/startv_720p.m3u8?sid=x" to "https://www.startv.com.tr/",
            "https://dogus.daioncdn.net/dmax/dmax.m3u8?ce=3" to "https://www.dmax.com.tr/",
            "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web" to "https://www.kanald.com.tr/",
            "https://dogus-live.daioncdn.net/ntv/ntv.m3u8" to "https://www.ntv.com.tr/",
        )
        for ((url, referer) in expected) {
            val entry = M3u.Entry(
                title = "Test TV (1080p)",
                tvgId = null,
                logo = null,
                group = null,
                userAgent = null,
                referer = null,
                url = url,
            )
            assertEquals(url, referer, TrLiveChannels.refererFor(entry))
        }
    }

    @Test
    fun `listedeki referer harita degerinden oncelidir`() {
        // M3U listesi bazı kanallar için kendi referrer'ını taşır; haritaya
        // yalnızca listede değeri olmayanlar düşer.
        val entry = M3u.Entry(
            title = "V TV (720p)",
            tvgId = "VTV.tr@SD",
            logo = null,
            group = "News",
            userAgent = null,
            referer = "https://www.vtv.net.tr/",
            url = "https://ornek.stream/vtv.m3u8",
        )
        assertEquals("https://www.vtv.net.tr/", TrLiveChannels.refererFor(entry))
    }

    @Test
    fun `daion olmayan kanal referer istemez`() {
        // ercdn/yayin.com.tr gibi CDN'ler referer şart koşmuyor; göndermek
        // yerine boş bırakmak doğru: gereksiz başlık bazı oynatıcılarda
        // CORS öntestlerini boşa düşürür.
        val entry = M3u.Entry(
            title = "ATV (1080p)",
            tvgId = "ATV.tr@SD",
            logo = null,
            group = "General",
            userAgent = null,
            referer = null,
            url = "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_1080p.m3u8",
        )
        assertNull(TrLiveChannels.refererFor(entry))
    }

    @Test
    fun `bilinmeyen daion kanali referer istemez`() {
        val entry = M3u.Entry(
            title = "Bilinmeyen TV",
            tvgId = null,
            logo = null,
            group = null,
            userAgent = null,
            referer = null,
            url = "https://yeni.daioncdn.net/bilinmeyen/bilinmeyen.m3u8",
        )
        assertNull(TrLiveChannels.refererFor(entry))
    }

    @Test
    fun `bos refererli kayit daion haritasina duser`() {
        // referer boş dize olarak listelenmişse ("" -> isNotBlank filtresi
        // null'a çevirir) kanal bir Daion kanalıysa yedek harita kullanılır.
        val entry = M3u.Entry(
            title = "TV 8 (1080p)",
            tvgId = "TV8.tr@SD",
            logo = null,
            group = "General",
            userAgent = null,
            referer = null,
            url = "https://tv8-live.daioncdn.net/tv8/tv8.m3u8",
        )
        assertEquals("https://www.tv8.com.tr/", TrLiveChannels.refererFor(entry))
    }
}
