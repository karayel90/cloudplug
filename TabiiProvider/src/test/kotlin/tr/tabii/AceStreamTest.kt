package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AceStreamTest {

    /** Kullanıcının verdiği content ID: beIN SPORTS 1. */
    private val beinSports1 = "118416583faa3fb4f03800173429b6b844b43a52"

    @Test
    fun `gecerli content id kabul edilir`() {
        assertTrue(AceStream.isContentId(beinSports1))
        assertTrue(AceStream.isContentId("0000000000000000000000000000000000000000"))
        assertTrue(AceStream.isContentId("ABCDEF0123456789ABCDEF0123456789ABCDEF01"))
    }

    @Test
    fun `gecersiz content id reddedilir`() {
        assertFalse(AceStream.isContentId(""))
        assertFalse(AceStream.isContentId("118416583faa3fb4f03800173429b6b844b43a5"))   // 39 hane
        assertFalse(AceStream.isContentId("118416583faa3fb4f03800173429b6b844b43a521")) // 41 hane
        assertFalse(AceStream.isContentId("118416583faa3fb4f03800173429b6b844b43a5z"))  // harf degil
        assertFalse(AceStream.isContentId("http://127.0.0.1:6878/ace/manifest.m3u8"))
    }

    @Test
    fun `manifest adresi dogru kurulur`() {
        assertEquals(
            "http://127.0.0.1:6878/ace/manifest.m3u8?id=$beinSports1",
            AceStream.manifestUrl(beinSports1),
        )
    }

    @Test
    fun `acestream adresi dogru kurulur`() {
        assertEquals("acestream://$beinSports1", AceStream.acestreamUrl(beinSports1))
    }

    @Test
    fun `acestream adresinden content id cikarilir`() {
        assertEquals(beinSports1, AceStream.contentIdOf("acestream://$beinSports1"))
        assertEquals(beinSports1, AceStream.contentIdOf("  acestream://$beinSports1  "))
        assertEquals(beinSports1, AceStream.contentIdOf("acestream://$beinSports1?x=1"))
    }

    @Test
    fun `cs3 un ekledigi mainUrl onekli adres de taninir`() {
        // CS3 `MainAPI.fixUrl()` "http" ile baslamayan adresleri `mainUrl` ile
        // birlestirir: acestream://<id> -> https://www.tabii.com/acestream://<id>
        assertEquals(
            beinSports1,
            AceStream.contentIdOf("https://www.tabii.com/acestream://$beinSports1"),
        )
        assertEquals(
            beinSports1,
            AceStream.contentIdOf("https://www.tabii.com/acestream://$beinSports1?x=1"),
        )
    }

    @Test
    fun `tabii adresleri content id olarak algilanmaz`() {
        // tabii kanallarinin adresleri 6 haneli trackId tasir, 40 haneli
        // onaltilik ozet icermez.
        assertNull(AceStream.contentIdOf("https://www.tabii.com/tr/watch/live/trt1?trackId=150002"))
        assertNull(AceStream.contentIdOf("https://www.tabii.com/tr/watch/live/trt1"))
    }

    @Test
    fun `acestream olmayan adreslerden content id cikarilmaz`() {
        assertNull(AceStream.contentIdOf("https://www.tabii.com/tr/watch/live/trt1"))
        assertNull(AceStream.contentIdOf("acestream://kisa"))
        assertNull(AceStream.contentIdOf(""))
    }

    @Test
    fun `arama sonuclari ayristirilir`() {
        val json = """
            {"result":{"total":2,"results":[
              {"items":[{"status":2,"name":"beIN SPORTS 1","countries":["tr"],
                         "infohash":"$beinSports1","availability":1,"categories":["tv"]}],
               "name":"beIN SPORTS 1","icons":[{"url":"http://x/1.png","type":0}]},
              {"items":[{"status":1,"name":"beIN SPORTS 2","infohash":"48bee10b883f17c7186d60f1e8022dca0ba41a05",
                         "availability":0.5,"categories":["tv"]}],"name":"beIN SPORTS 2"}
            ],"time":0.24}}
        """.trimIndent()

        val results = AceStream.parseSearchResults(json)
        assertEquals(2, results.size)
        assertEquals("beIN SPORTS 1", results[0].name)
        assertEquals(beinSports1, results[0].contentId)
        assertEquals(2, results[0].status)
        assertEquals(1.0, results[0].availability, 0.001)
        assertEquals("48bee10b883f17c7186d60f1e8022dca0ba41a05", results[1].contentId)
        assertEquals(1, results[1].status)
    }

    @Test
    fun `bozuk arama yaniti bos liste doner`() {
        assertTrue(AceStream.parseSearchResults("bozuk json").isEmpty())
        assertTrue(AceStream.parseSearchResults("{}").isEmpty())
        assertTrue(AceStream.parseSearchResults("""{"result":{}}""").isEmpty())
    }

    @Test
    fun `gecersiz content id iceren arama sonucu atilir`() {
        val json = """{"result":{"results":[{"items":[{"name":"X","infohash":"kisa"}]}]}}"""
        assertTrue(AceStream.parseSearchResults(json).isEmpty())
    }

    @Test
    fun `en iyi eslesme secilir`() {
        val results = listOf(
            AceStreamResult("beIN SPORTS 2", "48bee10b883f17c7186d60f1e8022dca0ba41a05", 2, 1.0),
            AceStreamResult("beIN SPORTS 1", beinSports1, 2, 1.0),
            AceStreamResult("beIN SPORTS 1 HD", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 2, 1.0),
        )
        val best = AceStream.pickBest(results, "beIN SPORTS 1")
        assertNotNull(best)
        assertEquals(beinSports1, best!!.contentId)
    }

    @Test
    fun `yayinda olmayan sonuc dusuk puan alir`() {
        val results = listOf(
            AceStreamResult("beIN SPORTS 1", beinSports1, 1, 0.2),
            AceStreamResult("beIN SPORTS 1 TR", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 2, 1.0),
        )
        // tam eslesme, onek eslesmesine gore daha yuksek puan alir
        val best = AceStream.pickBest(results, "beIN SPORTS 1")
        assertEquals(beinSports1, best?.contentId)
    }

    @Test
    fun `bos sonuclarda secim yapilmaz`() {
        assertNull(AceStream.pickBest(emptyList(), "beIN SPORTS 1"))
        assertNull(AceStream.pickBest(listOf(AceStreamResult("X", beinSports1, 2, 1.0)), ""))
    }

    @Test
    fun `kanal adlari sadelestirilir`() {
        assertEquals("beinsports1", AceStream.normalize("beIN SPORTS 1"))
        assertEquals("beinsports1", AceStream.normalize("beIN  SPORTS-1"))
        assertEquals("setantasports", AceStream.normalize("Setanta Sports"))
    }

    // --- Asagidaki fixture'lar mock Ace Stream motorunun (127.0.0.1:6878)
    // gercek HTTP yanitlaridir; motor API'siyle uyumlulugu dogrular. ---

    @Test
    fun `motorun gercek search yaniti ayristirilir`() {
        val json = """{"result": {"total": 2, "results": [{"items": [{"status": 2, "name": "beIN SPORTS 1", "infohash": "$beinSports1", "availability": 1.0, "categories": ["tv"]}, {"status": 1, "name": "beIN SPORTS 1 HD", "infohash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "availability": 0.3, "categories": ["tv"]}], "name": "bein sports 1", "icons": []}], "time": 0.01}}"""
        val results = AceStream.parseSearchResults(json)
        assertEquals(2, results.size)
        assertEquals("beIN SPORTS 1", results[0].name)
        assertEquals(beinSports1, results[0].contentId)
        assertEquals(2, results[0].status)
        // "beIN SPORTS 1 HD" tam eslesmeden daha dusuk pynamali olmali
        assertEquals(beinSports1, AceStream.pickBest(results, "beIN SPORTS 1")?.contentId)
    }

    @Test
    fun `motorun gercek get_media_files yaniti ayristirilir`() {
        val json = """{"result": {"files": [{"index": 0, "filename": "beIN SPORTS 2"}], "name": "beIN SPORTS 2", "infohash": "48bee10b883f17c7186d60f1e8022dca0ba41a05", "transport_type": "bt", "type": "live"}}"""
        assertEquals("beIN SPORTS 2", AceStream.parseMediaName(json))
    }

    @Test
    fun `dosya adindan kanal adi cikarilir`() {
        val json = """{"result": {"files": [{"index": 0, "filename": "beIN SPORTS 3.ts"}]}}"""
        assertEquals("beIN SPORTS 3", AceStream.parseMediaName(json))
    }

    @Test
    fun `bozuk servis yanitinda kanal adi bulunamaz`() {
        assertNull(AceStream.parseMediaName("bozuk json"))
        assertNull(AceStream.parseMediaName("{}"))
        assertNull(AceStream.parseMediaName("""{"result":{}}"""))
    }

    @Test
    fun `json null degerleri metin olarak yorumlanmaz`() {
        // Jackson'da JSON null -> NullNode -> asText() == "null" olurdu.
        val search = """{"result":{"results":[{"items":[{"name":null,"infohash":"$beinSports1","status":null,"availability":null}]}]}}"""
        val results = AceStream.parseSearchResults(search)
        assertEquals(1, results.size)
        assertEquals("", results[0].name)
        assertNull(results[0].status)
        assertEquals(0.0, results[0].availability, 0.001)

        assertNull(AceStream.parseMediaName("""{"result":{"name":null,"files":[]}}"""))
    }

    // --- Oynatma adresi cozumleme (redirect sorunu) ---

    @Test
    fun `motor hata mesaji okunur`() {
        val json = """{"error":{"message":"unknown content id","code":404},"response":null}"""
        assertEquals("unknown content id (kod 404)", AceStream.parseEngineError(json))
    }

    @Test
    fun `motor hatayi metin olarak donerse de okunur`() {
        // Bazi surumler hatayi dogrudan metin olarak doner; once "bilinmeyen
        // hata" diyorduk ve motorun mesaji kayboluyordu.
        assertEquals(
            "Content not found",
            AceStream.parseEngineError("""{"error":"Content not found","response":null}"""),
        )
    }

    @Test
    fun `motor hatasiz yanit hata mesaji sayilmaz`() {
        assertNull(AceStream.parseEngineError("""{"response":{"playback_url":"x"},"error":null}"""))
        assertNull(AceStream.parseEngineError("""{"response":{}}"""))
        assertNull(AceStream.parseEngineError("bozuk json"))
    }

    @Test
    fun `manifest adresi content id iceriyor`() {
        assertTrue(AceStream.manifestUrl(beinSports1).contains(beinSports1))
        assertTrue(AceStream.manifestUrl(beinSports1).contains("/ace/manifest.m3u8"))
    }

    @Test
    fun `ilgisiz arama sonucu secilmez`() {
        // Kanal adiyla hicbir iliskisi olmayan sonuc secilmemeli; yanlis
        // kanal oynatmak yerine eski kimlik kullanilmali.
        val results = listOf(
            AceStreamResult("Eurosport 5", "cccccccccccccccccccccccccccccccccccccccc", 2, 1.0),
            AceStreamResult("Kanal D", "dddddddddddddddddddddddddddddddddddddddd", 2, 1.0),
        )
        assertNull(AceStream.pickBest(results, "beIN SPORTS 1"))
    }

    @Test
    fun `onek eslesmesi artik kabul edilmiyor`() {
        // Onceki davranis: duzleştirilmis string karsilastirmasinda onek
        // eslesmesi 400 puan aliyordu; "beIN SPORTS 1 TR" yanlis kanal
        // olarak secilebiliyordu.
        val results = listOf(
            AceStreamResult("beIN SPORTS 1 HD", "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", 2, 1.0),
        )
        assertNull(AceStream.pickBest(results, "beIN SPORTS 1"))
    }

    @Test
    fun `token eslesmesi farkli yayinlari karistirmaz`() {
        // "beIN SPORTS 1" ile "beIN SPORTS 1 TR" FARKLI yayinlardir.
        val target = AceStream.tokens("beIN SPORTS 1")
        assertFalse(AceStream.matchesTokens("beIN SPORTS 1 TR", target))
        assertFalse(AceStream.matchesTokens("beIN SPORTS 1 HD", target))
        assertFalse(AceStream.matchesTokens("BEIN SPORTS 1 FR", target))
        assertFalse(AceStream.matchesTokens("beIN SPORTS 2", target))

        assertTrue(AceStream.matchesTokens("beIN SPORTS 1", target))
        assertTrue(AceStream.matchesTokens("Bein Sports 1", target))
        assertTrue(AceStream.matchesTokens("bein sports 1", target))
    }

    @Test
    fun `token sirasi degisse de eslesir`() {
        val target = AceStream.tokens("beIN SPORTS 1")
        assertTrue(AceStream.matchesTokens("SPORTS BEIN 1", target))
    }

    @Test
    fun `token listesi dogru ayristirilir`() {
        assertEquals(listOf("bein", "sports", "1"), AceStream.tokens("beIN SPORTS 1"))
        assertEquals(listOf("bein", "sports", "1", "tr"), AceStream.tokens("beIN SPORTS 1 TR"))
        assertEquals(listOf("setanta", "sports"), AceStream.tokens("Setanta Sports"))
        assertTrue(AceStream.tokens("!!!").isEmpty())
    }

    @Test
    fun `arama sonuclarinin tamami kullanilabilirlige gore siralanir`() {
        // Ayni kanalin birden fazla yayinci node'u olabiliyor; en kullanilabilir
        // olan once denenmeli.
        val results = listOf(
            AceStreamResult("beIN SPORTS 1", "1111111111111111111111111111111111111111", 1, 0.2),
            AceStreamResult("beIN SPORTS 1", "2222222222222222222222222222222222222222", 2, 0.9),
            AceStreamResult("beIN SPORTS 1", "3333333333333333333333333333333333333333", 2, 0.4),
        )
        val target = AceStream.tokens("beIN SPORTS 1")
        val ordered = results
            .filter { AceStream.score(it, target) >= AceStream.MIN_MATCH_SCORE }
            .sortedByDescending {
                (if (it.status == AceStream.STATUS_AVAILABLE) 10_000 else 0) +
                    (it.availability * 1000).toInt()
            }
            .map { it.contentId }
        assertEquals(
            listOf(
                "2222222222222222222222222222222222222222",
                "3333333333333333333333333333333333333333",
                "1111111111111111111111111111111111111111",
            ),
            ordered,
        )
    }
}
