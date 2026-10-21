package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class TrLiveChannelsTest {

    @Test
    fun `kanal kimlikleri benzersiz`() {
        val ids = TrLiveChannels.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `her kanalin zorunlu alanlari dolu`() {
        for (c in TrLiveChannels.ALL) {
            assertTrue("${c.id}: id boş", c.id.isNotBlank())
            assertTrue("${c.id}: ad boş", c.name.isNotBlank())
            assertTrue("${c.id}: sayfa boş", c.pageUrl.isNotBlank())
            assertTrue("${c.id}: logo boş", !c.logo.isNullOrBlank())
            assertTrue("${c.id}: https olmalı", c.streamUrl.startsWith("https://"))
            assertTrue("${c.id}: m3u8 olmalı", c.streamUrl.contains(".m3u8"))
        }
    }

    @Test
    fun `manifest adresi stream adresinin prefixi`() {
        for (c in TrLiveChannels.ALL) {
            assertTrue("${c.id}: manifest prefix değil", c.streamUrl.startsWith(c.manifestUrl))
        }
    }

    @Test
    fun `kimlikle kanal bulma`() {
        assertEquals("TV8", TrLiveChannels.byId("tv8")?.name)
        assertEquals("ATV", TrLiveChannels.byId("atv")?.name)
        assertEquals("Star TV", TrLiveChannels.byId("startv")?.name)
        assertEquals("DMAX", TrLiveChannels.byId("dmax")?.name)
        assertNull(TrLiveChannels.byId("bilinmeyen"))
    }

    @Test
    fun `sayfa adresiyle kanal bulma`() {
        // Cloudstream arama sonucundaki mutlak adresi load() fonksiyonuna
        // oldugu gibi geçiriyor.
        for (c in TrLiveChannels.ALL) {
            assertEquals(c, TrLiveChannels.byPageUrl(c.pageUrl))
        }
    }

    @Test
    fun `yayin adresinden kanal geri cozuluyor`() {
        val atv = TrLiveChannels.byId("atv")!!
        // Gerçek oynatıcı imzalı adresi döndürür; adres yine de manifest ile başlar.
        val signed = atv.manifestUrl + "?ce=3&app=" + TrLiveChannels.ATV_APP_ID + "&st=abc&e=123"
        assertEquals(atv, TrLiveChannels.byStreamUrl(signed))
    }

    @Test
    fun `farkli kanallarin manifest adresleri birbirine karismiyor`() {
        val atv = TrLiveChannels.byId("atv")!!
        val tv8 = TrLiveChannels.byId("tv8")!!
        assertFalse(atv.manifestUrl.startsWith(tv8.manifestUrl))
        assertFalse(tv8.manifestUrl.startsWith(atv.manifestUrl))
    }

    @Test
    fun `token adresi yayin adresini kodluyor`() {
        val stream = "https://trkvz.daioncdn.net/atv/atv.m3u8?ce=3&app=abc-123"
        val url = buildTokenUrl(stream, random = 4242)

        assertTrue(url.startsWith(TrLiveChannels.TOKEN_HOST))
        assertTrue(url.contains("?4242&url="))

        val encoded = url.substringAfter("?4242&url=")
        assertEquals(stream, URLDecoder.decode(encoded, "UTF-8"))
    }

    @Test
    fun `sadece atv token istiyor`() {
        val tokenized = TrLiveChannels.ALL.filter { it.requiresToken }.map { it.id }
        assertEquals(listOf("atv"), tokenized)
    }

    @Test
    fun `atv yayin adresi dogru daion kanalini kullaniyor`() {
        val atv = TrLiveChannels.byId("atv")!!
        assertTrue(atv.manifestUrl.contains("/atv/atv.m3u8"))
        assertTrue(atv.streamUrl.contains(TrLiveChannels.ATV_APP_ID))
    }

    @Test
    fun `her kanalin sayfa adresi dogru siteye isaret ediyor`() {
        val expected = mapOf(
            "tv8" to "tv8.com.tr",
            "atv" to "atv.com.tr",
            "startv" to "startv.com.tr",
            "dmax" to "dmax.com.tr",
        )
        for ((id, host) in expected) {
            val c = TrLiveChannels.byId(id)
            assertNotNull("$id bulunamadı", c)
            assertTrue("$id sayfası $host olmalı", c!!.pageUrl.contains(host))
            assertTrue("$id referer'ı $host olmalı", c.referer.contains(host))
        }
    }
}
