package tr.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {

    private val atvList = """
        #EXTM3U
        #EXTINF:-1 tvg-id="ATV.tr@SD" tvg-logo="https://i.imgur.com/HyVUwFC.png" group-title="General",ATV (360p)
        http://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_360p.m3u8
        #EXTINF:-1 tvg-id="ATV.tr@SD" tvg-logo="https://i.imgur.com/HyVUwFC.png" group-title="General",ATV (1080p)
        https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_1080p.m3u8
    """.trimIndent()

    @Test
    fun `temel ayristirma`() {
        val entries = M3u.parse(atvList)
        assertEquals(2, entries.size)
        assertEquals("ATV (360p)", entries[0].title)
        assertEquals("http://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_360p.m3u8", entries[0].url)
        assertEquals("ATV.tr@SD", entries[0].tvgId)
        assertEquals("https://i.imgur.com/HyVUwFC.png", entries[0].logo)
        assertEquals("General", entries[0].group)
    }

    @Test
    fun `cozunurluk puani basliktan okunur`() {
        assertEquals(1080, M3u.parse(atvList)[1].resolution)
        assertEquals(360, M3u.parse(atvList)[0].resolution)
    }

    @Test
    fun `cift kanal en yuksek cozunurlukten birakilir`() {
        val deduped = M3u.dedupeByBestResolution(M3u.parse(atvList))
        assertEquals(1, deduped.size)
        assertEquals(1080, deduped[0].resolution)
        assertTrue(deduped[0].url.endsWith("atv_1080p.m3u8"))
    }

    @Test
    fun `esit cozunurlukte https tercih edilir`() {
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1,Bursa TV (720p)
            http://win1.yayin.com.tr/bursatv/bursatv/playlist.m3u8
            #EXTINF:-1,Bursa TV (720p)
            https://cdn-bursatv.yayin.com.tr/bursatv/bursatv/playlist.m3u8
            """.trimIndent()
        )
        val deduped = M3u.dedupeByBestResolution(entries)
        assertEquals(1, deduped.size)
        assertTrue(deduped[0].url.startsWith("https://"))
    }

    @Test
    fun `cozunurluk olcekleme 4k ve 8k`() {
        assertEquals(2160, M3u.resolutionOf("TRT 1 4K"))
        assertEquals(4320, M3u.resolutionOf("TRT 1 8K"))
        assertEquals(2160, M3u.resolutionOf("TRT 1 (2160p)"))
        assertEquals(720, M3u.resolutionOf("24 TV (720p)"))
        assertEquals(0, M3u.resolutionOf("NOW TV"))
    }

    @Test
    fun `extvlcopt user agent ve referrer takip eden kayda uygulanir`() {
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="VTV.tr@SD" group-title="News",V TV (720p)
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 özel araç
            #EXTVLCOPT:http-referrer=https://www.vtv.net.tr/
            https://ornek.stream/vtv.m3u8
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertEquals("Mozilla/5.0 özel araç", entries[0].userAgent)
        assertEquals("https://www.vtv.net.tr/", entries[0].referer)
    }

    @Test
    fun `user-agent iceren baslik bozulmadan okunur`() {
        // Gerçek listeden: user-agent değeri virgül içeriyor; başlık yine de
        // son `",` dizisinden sonra gelmelidir.
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="24TV.tr@SD" tvg-logo="https://i.imgur.com/8FO41es.png" user-agent="Mozilla/5.0 Macintosh; Intel Mac OS X 10_14_5 AppleWebKit/537.36 KHTML, like Gecko Chrome/76.0.3809.25 Safari/537.36",24 TV (1080p)
            https://mn-nl.mncdn.com/kanal24/smil:kanal24.smil/playlist.m3u8
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertEquals("24 TV (1080p)", entries[0].title)
        assertEquals(
            "Mozilla/5.0 Macintosh; Intel Mac OS X 10_14_5 AppleWebKit/537.36 KHTML, like Gecko Chrome/76.0.3809.25 Safari/537.36",
            entries[0].userAgent,
        )
    }

    @Test
    fun `turkce karakterler duzlestirilir`() {
        assertEquals("benguturktv", M3u.normalizeName("Benguturk TV (1080p)"))
        assertEquals("benguturktv", M3u.normalizeName("BENGÜTÜRK TV (720p) [Not 24/7]"))
        assertEquals("trtmuzik", M3u.normalizeName("TRT Muzik"))
        assertEquals("trtmuzik", M3u.normalizeName("TRT Müzik (720p)"))
    }

    @Test
    fun `benzer adlar cift kanal sayilir`() {
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="X.tr@SD",Lalegul TV (1080p)
            https://a.example/lalegul1080.m3u8
            #EXTINF:-1 tvg-id="X.tr@SD",Lalegül TV (720p)
            https://b.example/lalegul720.m3u8
            """.trimIndent()
        )
        val deduped = M3u.dedupeByBestResolution(entries)
        assertEquals(1, deduped.size)
        assertEquals(1080, deduped[0].resolution)
    }

    @Test
    fun `farkli kanallar birlestirilmez`() {
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1,Kanal D (1080p)
            https://a.example/kanald.m3u8
            #EXTINF:-1,Kanal D Drama (1080p)
            https://b.example/kanalddrama.m3u8
            #EXTINF:-1,Vive Kanal D Drama (1080p)
            https://c.example/vivekanald.m3u8
            """.trimIndent()
        )
        val deduped = M3u.dedupeByBestResolution(entries)
        // "Kanal D" ve "Kanal D Drama" farklı kanallar; "Kanal D Drama" ile
        // "Vive Kanal D Drama" ise ayrı yayın akışlarıdır.
        assertEquals(3, deduped.size)
    }

    @Test
    fun `ayni tvg-id farkli adlar tek kanal sayilmaz`() {
        // iptv-org meta verisi hatalı olabilir: "24 TV", "TV 24" ve "TR24 TV"
        // aynı tvg-id'yi taşır ama muhtemelen farklı kanallardır. Ad esaslı
        // eşleştirme yanlış birleştirmeyi engeller.
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="24TV.tr@SD",24 TV (1080p)
            https://a.example/tv24.m3u8
            #EXTINF:-1 tvg-id="24TV.tr@SD",TV 24 (720p)
            https://b.example/tv24b.m3u8
            #EXTINF:-1 tvg-id="24TV.tr@SD",TR24 TV (720p)
            https://c.example/tr24.m3u8
            """.trimIndent()
        )
        val deduped = M3u.dedupeByBestResolution(entries)
        assertEquals(3, deduped.size)
    }

    @Test
    fun `adresi olmayan kayit atlanir`() {
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1,Bozuk Kayit (720p)
            #EXTINF:-1,Saglam Kayit (720p)
            https://a.example/saglam.m3u8
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertEquals("Saglam Kayit (720p)", entries[0].title)
    }

    @Test
    fun `bos ve yorum satirlari atlanir`() {
        val entries = M3u.parse(
            """
            #EXTM3U

            # Kaynak: ornek
            #EXTINF:-1,Deneme TV (1080p)

            https://a.example/deneme.m3u8
            """.trimIndent()
        )
        assertEquals(1, entries.size)
        assertNull(entries[0].tvgId)
        assertNull(entries[0].logo)
        assertNull(entries[0].group)
    }

    @Test
    fun `normalizasyon bos basligi bos anahtara cevirir`() {
        assertEquals("", M3u.normalizeName("(1080p) [Not 24/7]"))
        // Boş anahtarlı kayıtlar tekilleştirmeye katılmaz.
        val entries = M3u.parse(
            """
            #EXTM3U
            #EXTINF:-1,(720p) [Not 24/7]
            https://a.example/bilinmeyen.m3u8
            """.trimIndent()
        )
        assertTrue(M3u.dedupeByBestResolution(entries).isEmpty())
    }
}
