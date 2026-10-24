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

    @Test
    fun `populer kanallar one siralanir`() {
        val sorted = TrLiveChannels.sortByPopularity(
            listOf(
                entry("TRT Müzik (720p)"),
                entry("Halk TV (1080p)"),
                entry("Kanal D (1080p)"),
                entry("ATV (1080p)"),
            )
        )
        assertEquals(
            listOf("ATV (1080p)", "Kanal D (1080p)", "Halk TV (1080p)", "TRT Müzik (720p)"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `bilinmeyen kanallar kendi arasinda sirasini korur`() {
        // Sıralamada olmayan kanallar M3U'daki (alfabetik) sıralarını korur;
        // her çekilişte yer değiştirmemeleri gerekir.
        val sorted = TrLiveChannels.sortByPopularity(
            listOf(
                entry("Zeta TV (720p)"),
                entry("Alfa TV (720p)"),
                entry("ATV (1080p)"),
            )
        )
        assertEquals(
            listOf("ATV (1080p)", "Zeta TV (720p)", "Alfa TV (720p)"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `cozunurluk sonekli baslik populer kanal sayilir`() {
        // "TRT 1 4K" başlığı "trt14k" olarak düzleşir; yine de TRT 1 sayılmalı.
        val sorted = TrLiveChannels.sortByPopularity(
            listOf(
                entry("Bilinmeyen TV (1080p)"),
                entry("TRT 1 4K"),
            )
        )
        assertEquals(listOf("TRT 1 4K", "Bilinmeyen TV (1080p)"), sorted.map { it.title })
    }

    @Test
    fun `yerel atv kanali atv ile eslesmez`() {
        // "ATV Alanya" soneki çözünürlük etiketi değil; ATV sırasına girmemeli.
        val sorted = TrLiveChannels.sortByPopularity(
            listOf(
                entry("ATV Alanya (720p)"),
                entry("ATV (1080p)"),
            )
        )
        assertEquals(listOf("ATV (1080p)", "ATV Alanya (720p)"), sorted.map { it.title })
    }

    @Test
    fun `olu adresler kataloga girmez`() {
        val deadUrl = DeadStreams.URLS.first()
        val result = TrLiveChannels.withoutDeadStreams(
            listOf(
                entry("Ölü TV (1080p)", deadUrl),
                entry("Canlı TV (720p)", "https://ornek.stream/canli.m3u8"),
            )
        )
        assertEquals(listOf("Canlı TV (720p)"), result.map { it.title })
    }

    @Test
    fun `olu en iyi kayit yerine calisan kayit kullanilir`() {
        // En yüksek çözünürlüklü kayıt ölüyse kanal kaybolmamalı; çalışan
        // daha düşük çözünürlüklü kayıt devreye girmeli.
        val deadUrl = DeadStreams.URLS.first()
        val result = M3u.dedupeByBestResolution(
            TrLiveChannels.withoutDeadStreams(
                listOf(
                    entry("Örnek TV (1080p)", deadUrl),
                    entry("Örnek TV (720p)", "https://ornek.stream/ornek-tv-720p.m3u8"),
                )
            )
        )
        assertEquals(listOf("Örnek TV (720p)"), result.map { it.title })
    }

    @Test
    fun `kara liste bilinmeyen adresi etkilemez`() {
        val result = TrLiveChannels.withoutDeadStreams(
            listOf(entry("Bilinmeyen TV (720p)", "https://ornek.stream/bilinmeyen.m3u8"))
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `liste disi kanallar kataloga eklenir`() {
        val keys = TrLiveChannels.withExtraChannels(emptyList()).map { M3u.normalizeName(it.title) }
        assertEquals(listOf("showtv", "beyaztv", "tv2", "dmax", "tlc", "beinsports1server", "beinsports2server", "beinsports3server"), keys)
    }

    @Test
    fun `ek kanal daha yuksek cozunurlukle m3u kaydini ezer`() {
        // M3U'daki Beyaz TV 720p kaydı ölü/eksik; ek kayıt 1080p olduğu için
        // çift kanal temizliğinde o kazanmalı.
        val result = M3u.dedupeByBestResolution(
            TrLiveChannels.withExtraChannels(
                listOf(entry("Beyaz TV (720p)", "https://ornek.stream/beyaz-720p.m3u8"))
            )
        )
        val beyaz = result.first { M3u.normalizeName(it.title) == "beyaztv" }
        assertEquals("Beyaz TV (1080p)", beyaz.title)
        assertEquals("https://beyaztv-live.daioncdn.net/beyaztv/beyaztv_1080p.m3u8", beyaz.url)
    }

    @Test
    fun `ek kanallarin refereri korunur`() {
        val showTv = ExtraChannels.ENTRIES.first { M3u.normalizeName(it.title) == "showtv" }
        assertEquals("https://www.showtv.com.tr/", TrLiveChannels.refererFor(showTv))
    }

    @Test
    fun `populerlik listesi katalogdaki tum kanallari kapsar`() {
        // Liste baştan sona editoryal sıradır; bilinen tüm kanallar burada
        // olmalı ki sıralama M3U düzenine bağlı kalmasın. TV2 ve CNBC-e gibi
        // kanallar da sıraya dahil.
        val ranked = TrLiveChannels.rankedKeys
        assertEquals(ranked.size, ranked.toSet().size)
        assertTrue("cnbce" in ranked)
        assertTrue("tv2" in ranked)
        assertEquals(ranked.indexOf("cnbce") + 1, ranked.indexOf("tv2"))
    }

    private fun entry(
        title: String,
        url: String = "https://ornek.stream/" + M3u.normalizeName(title) + ".m3u8",
    ): M3u.Entry = M3u.Entry(
        title = title,
        tvgId = null,
        logo = null,
        group = null,
        userAgent = null,
        referer = null,
        url = url,
    )
}
