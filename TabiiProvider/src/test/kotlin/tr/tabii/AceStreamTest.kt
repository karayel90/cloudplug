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
    fun `acestream adresi dogru kurulur`() {
        assertEquals("acestream://$beinSports1", AceStream.acestreamUrl(beinSports1))
    }

    @Test
    fun `content_id ve infohash farkli parametre kullanir`() {
        // Cihazda olculdu:
        //   ?content_id=<content_id> -> 302 (calisir)
        //   ?content_id=<infohash>   -> HTTP 500
        //   ?infohash=<infohash>     -> 302 (calisir)
        //   ?infohash=<content_id>   -> HTTP 500
        val contentIdRef = AceContentRef(beinSports1, isInfohash = false)
        val infohashRef = AceContentRef(beinSports1, isInfohash = true)

        assertTrue(contentIdRef.manifestUrl().contains("content_id="))
        assertFalse(contentIdRef.manifestUrl().contains("infohash="))
        assertTrue(infohashRef.manifestUrl().contains("infohash="))
        assertFalse(infohashRef.manifestUrl().contains("content_id="))

        assertTrue(contentIdRef.checkUrl().contains("content_id="))
        assertTrue(infohashRef.checkUrl().contains("infohash="))
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
    fun `eslesen sonuclar hedefi kapsayanlarin temizi once gelir`() {
        // Hedefin TUM kelimelerini kapsayan adaylar eslesir; temiz eslesme
        // (fazla kelimesiz) siralamada once gelir.
        val results = listOf(
            AceStreamResult("beIN SPORTS 2", "48bee10b883f17c7186d60f1e8022dca0ba41a05", 2, 1.0),
            AceStreamResult("beIN SPORTS 1 HD", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 2, 1.0),
            AceStreamResult("beIN SPORTS 1", beinSports1, 2, 1.0),
        )
        val target = AceStream.tokens("beIN SPORTS 1")
        val matched = results
            .filter { AceStream.matchesTokens(it.name, target) }
            .sortedWith(AceStream.searchOrder(target))
        assertEquals(2, matched.size)
        assertEquals(beinSports1, matched[0].contentId)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", matched[1].contentId)
    }

    @Test
    fun `yayinda olmayan sonuc düşük sıralanır`() {
        // "beIN SPORTS 1 TR" hedefin tum kelimelerini kapsadigi icin
        // artik ELENMEZ; ama tribun siralamasinda yayinda olmayan (status)
        // adayin altinda kalir.
        val results = listOf(
            AceStreamResult("beIN SPORTS 1", beinSports1, 1, 0.2),
            AceStreamResult("beIN SPORTS 1 TR", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 2, 1.0),
        )
        val target = AceStream.tokens("beIN SPORTS 1")
        val matched = results
            .filter { AceStream.matchesTokens(it.name, target) }
            .sortedWith(AceStream.searchOrder(target))
        assertEquals(2, matched.size)
        // Yayinda olan (status=2) aday once gelir; temiz eslesme ikincil kriterdir.
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", matched[0].contentId)
        assertEquals(beinSports1, matched[1].contentId)
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
        // "beIN SPORTS 1 HD" fazla kelime olarak kabul edilir; siralamada
        // temiz eslesmenin (beIN SPORTS 1) altinda kalir.
        val target = AceStream.tokens("beIN SPORTS 1")
        val ordered = results
            .filter { AceStream.matchesTokens(it.name, target) }
            .sortedWith(AceStream.searchOrder(target))
            .map { it.contentId }
        assertEquals(
            listOf(beinSports1, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            ordered,
        )
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
    fun `manifest adresi content id iceriyor`() {
        val url = AceContentRef(beinSports1).manifestUrl()
        assertTrue(url.contains(beinSports1))
        assertTrue(url.contains("/ace/manifest.m3u8"))
        // Motorun kanonik parametresi content_id (id eski takma ad)
        assertTrue(url.contains("content_id="))
    }

    @Test
    fun `ilgisiz arama sonucu secilmez`() {
        // Kanal adiyla hicbir iliskisi olmayan sonuc secilmemeli; yanlis
        // kanal oynatmak yerine eski kimlik kullanilmali.
        val results = listOf(
            AceStreamResult("Eurosport 5", "cccccccccccccccccccccccccccccccccccccccc", 2, 1.0),
            AceStreamResult("Kanal D", "dddddddddddddddddddddddddddddddddddddddd", 2, 1.0),
        )
        val target = AceStream.tokens("beIN SPORTS 1")
        assertTrue(results.none { AceStream.matchesTokens(it.name, target) })
    }

    @Test
    fun `fazla kelimeler kabul edilir ama cezalandirilir`() {
        // Eski davranista tam token esitligi isteniyordu; dizindeki
        // "beIN SPORTS HD 1 [TR]" gibi turetilmis adlar yuklenemiyordu.
        // Artik hedefin TUM kelimeleri adayda mevcutsa eslesme sayilir,
        // fazla kelimeler siralamada ceza alir.
        val target = AceStream.tokens("beIN SPORTS 1")
        assertTrue(AceStream.matchesTokens("beIN SPORTS 1 HD", target))
        assertEquals(1, AceStream.extraTokens("beIN SPORTS 1 HD", target))
        assertEquals(0, AceStream.extraTokens("beIN SPORTS 1", target))
    }

    @Test
    fun `token eslesmesi farkli yayinlari karistirmaz`() {
        // Hedefin TUM kelimeleri adayda mevcut olmali; bu yuzden:
        // - kanal numarasi farkli olan aday elenir (hedefteki "1" yok),
        // - hedefte dil kelimesi varsa onu kapsamayan aday elenir.
        assertFalse(AceStream.matchesTokens("beIN SPORTS 2", AceStream.tokens("beIN SPORTS 1")))

        val trTarget = AceStream.tokens("beIN SPORTS 1 TR")
        assertFalse(AceStream.matchesTokens("beIN SPORTS 1", trTarget))       // TR eksik
        assertFalse(AceStream.matchesTokens("BEIN SPORTS 1 FR", trTarget))     // TR yerine FR
        assertFalse(AceStream.matchesTokens("beIN SPORTS 2 TR", trTarget))     // kanal no farkli

        assertTrue(AceStream.matchesTokens("beIN SPORTS 1 TR", trTarget))      // temiz eslesme
        assertTrue(AceStream.matchesTokens("beIN SPORTS 1 TR HD", trTarget))   // fazla kelime kabul
        assertTrue(AceStream.matchesTokens("beIN SPORTS HD 1 TR", trTarget))   // kelime sirasi farkli

        val bareTarget = AceStream.tokens("beIN SPORTS 1")
        assertTrue(AceStream.matchesTokens("beIN SPORTS 1", bareTarget))
        assertTrue(AceStream.matchesTokens("Bein Sports 1", bareTarget))
        assertTrue(AceStream.matchesTokens("bein sports 1", bareTarget))
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
        // olan once denenmeli. Siralama kurallari [AceStream.searchOrder]
        // icerisinde toplandi: status -> fazla kelime cezasi -> availability.
        val results = listOf(
            AceStreamResult("beIN SPORTS 1", "1111111111111111111111111111111111111111", 1, 0.2),
            AceStreamResult("beIN SPORTS 1", "2222222222222222222222222222222222222222", 2, 0.9),
            AceStreamResult("beIN SPORTS 1", "3333333333333333333333333333333333333333", 2, 0.4),
        )
        val target = AceStream.tokens("beIN SPORTS 1")
        val ordered = results
            .filter { AceStream.matchesTokens(it.name, target) }
            .sortedWith(AceStream.searchOrder(target))
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

    // --- v26 yeni davranislari: saf yardimcilar ---

    @Test
    fun `describe adresleri parametre turune gore kullanilir`() {
        // Cihazda olculdu: yanlis parametre HTTP 500 doner; bu yuzden
        // degerin turu neyse o parametre basta denenir, diger parametre
        // yedek olarak denemeye girer.
        val contentUrl = AceStream.describeUrl(beinSports1, isInfohash = false)
        assertTrue(contentUrl.contains("content_id=$beinSports1"))
        assertFalse(contentUrl.contains("infohash="))

        val hashUrl = AceStream.describeUrl(beinSports1, isInfohash = true)
        assertTrue(hashUrl.contains("infohash=$beinSports1"))
        assertFalse(hashUrl.contains("content_id="))
    }

    @Test
    fun `motor durum onbellegi sureye gore yasar`() {
        // Onbellek yok (0 damgasi) -> taze degil.
        assertFalse(AceStream.isEngineStateFresh(0L, 1_000L))
        // Damga guncel -> taze.
        assertTrue(AceStream.isEngineStateFresh(10_000L, 15_000L))
        // Damga eski -> taze degil.
        assertFalse(AceStream.isEngineStateFresh(10_000L, 30_000L))
    }

    @Test
    fun `calisan ref onbellegi sureye gore yasar`() {
        // Onbellek yok (0 damgasi) -> taze degil.
        assertFalse(AceStream.isWorkingRefFresh(0L, 1_000L))
        // Damga guncel -> taze.
        assertTrue(AceStream.isWorkingRefFresh(1_000_000L, 1_100_000L))
        // Damga eski -> taze degil. 15 dk = 900_000 tanesinde.
        assertFalse(AceStream.isWorkingRefFresh(1_000_000L, 1_000_000L + AceStream.WORKING_REF_TTL_MS))
    }

    @Test
    fun `calisan ref onbellegine yazilir ve okunur`() {
        val key = "test-kanal"
        AceStream.forgetWorkingRef(key)
        assertNull(AceStream.workingRef(key))

        AceStream.rememberWorkingRef(key, AceContentRef(beinSports1))
        assertEquals(AceContentRef(beinSports1), AceStream.workingRef(key))

        AceStream.forgetWorkingRef(key)
        assertNull(AceStream.workingRef(key))
    }

    @Test
    fun `ustel bekleme adimi tavana kadar buyur`() {
        assertEquals(1_000L, AceStream.nextBackoff(500L, 3_000L))
        assertEquals(2_000L, AceStream.nextBackoff(1_000L, 3_000L))
        // Tavan: 4 sn yerine 3 sn.
        assertEquals(3_000L, AceStream.nextBackoff(2_000L, 3_000L))
        assertEquals(3_000L, AceStream.nextBackoff(3_000L, 3_000L))
    }
}
