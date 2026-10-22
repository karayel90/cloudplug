package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AceStreamChannelsTest {

    @Test
    fun `kanal kimlikleri benzersiz`() {
        val ids = AceStreamChannels.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `sadece uc kanal var`() {
        // Sadece Turkce beIN SPORTS HD 1-3 kanallari destekleniyor.
        assertEquals(3, AceStreamChannels.ALL.size)
        assertEquals(
            listOf(
                "beIN SPORTS HD 1 [TR]",
                "beIN SPORTS HD 2 [TR]",
                "beIN SPORTS HD 3 [TR]",
            ),
            AceStreamChannels.ALL.map { it.name },
        )
    }

    @Test
    fun `her kanalin gecerli content id si var`() {
        for (c in AceStreamChannels.ALL) {
            assertTrue("${c.id}: content ID listesi bos", c.contentIds.isNotEmpty())
            for (id in c.contentIds) {
                assertTrue("${c.id}: content id gecersiz: $id", AceStream.isContentId(id))
            }
            assertTrue("${c.id}: ad bos", c.name.isNotBlank())
            assertTrue("${c.id}: logo bos", !c.logo.isNullOrBlank())
        }
    }

    @Test
    fun `content ID'ler kanal icinde benzersiz`() {
        for (c in AceStreamChannels.ALL) {
            assertEquals("${c.id}: yinelenen content ID", c.contentIds.size, c.contentIds.toSet().size)
        }
    }

    @Test
    fun `her kanalin arama adi var`() {
        for (c in AceStreamChannels.ALL) {
            assertTrue("${c.id}: arama adi bos", c.searchNames.isNotEmpty())
            for (n in c.searchNames) {
                assertTrue("${c.id}: bos arama adi", n.isNotBlank())
            }
        }
    }

    @Test
    fun `arama adlari kanal basina benzersiz`() {
        // Ayni arama adi iki farkli kanalda kullanilirsa arama sonuclari
        // karisir ve yanlis kanal oynatilir.
        val all = AceStreamChannels.ALL.flatMap { it.searchNames }.map { it.lowercase() }
        assertEquals("yinelenen arama adi", all.size, all.toSet().size)
    }

    @Test
    fun `arama adlari sadece HD varyantlarini icerir`() {
        // "beIN SPORTS 1 TR" (SD) farkli bir yayin; karistirmamak icin
        // arama adlarinda yer almaz.
        for (c in AceStreamChannels.ALL) {
            for (n in c.searchNames) {
                assertTrue("$n: HD olmali", n.contains("HD"))
            }
        }
    }

    @Test
    fun `kanal adlari Turkce HD versiyonlari`() {
        // Kanal adlari Ace Player'daki gosterimden alindi.
        for (c in AceStreamChannels.ALL) {
            assertTrue("${c.id}: HD olmali", c.name.contains("HD"))
            assertTrue("${c.id}: TR olmali", c.name.contains("TR"))
        }
    }

    @Test
    fun `content ID eşleşmeleri doğru`() {
        val expected = mapOf(
            "beIN SPORTS HD 1 [TR]" to "118416583faa3fb4f03800173429b6b844b43a52",
            "beIN SPORTS HD 2 [TR]" to "48bee10b883f17c7186d60f1e8022dca0ba41a05",
            "beIN SPORTS HD 3 [TR]" to "ed81c333815247536edfcdb2755147e8c4144d62",
        )
        for ((name, id) in expected) {
            val channel = AceStreamChannels.byName(name)
            assertNotNull("$name bulunamadi", channel)
            assertEquals(id, channel!!.contentId)
            assertEquals("acestream://$id", channel.pageUrl)
        }
    }

    @Test
    fun `content id ile kanal bulma`() {
        assertEquals(
            "beIN SPORTS HD 1 [TR]",
            AceStreamChannels.byContentId("118416583faa3fb4f03800173429b6b844b43a52")?.name,
        )
        // buyuk/kucuk harf fark etmemeli
        assertNotNull(AceStreamChannels.byContentId("118416583FAa3FB4F03800173429B6B844B43A52"))
        assertNull(AceStreamChannels.byContentId("0000000000000000000000000000000000000000"))
    }

    @Test
    fun `silinen kanallar artik yok`() {
        // Onceki listedeki diger P2P kanallari kaldirildi.
        assertNull(AceStreamChannels.byName("beIN SPORTS 1"))
        assertNull(AceStreamChannels.byName("beIN SPORTS 2"))
        assertNull(AceStreamChannels.byName("beIN SPORTS 3"))
        assertNull(AceStreamChannels.byName("beIN SPORTS 4"))
        assertNull(AceStreamChannels.byName("beIN SPORTS HABER"))
        assertNull(AceStreamChannels.byName("Setanta Sports"))
        assertNull(AceStreamChannels.byName("Setanta Sports Plus"))
        assertFalse(
            AceStreamChannels.ALL.any { it.contentIds.contains("47c1b3a68d03bd190fed521d144ad08fa0f195f3") }
        )
    }

    @Test
    fun `kimlik ve ad ile kanal bulma`() {
        assertEquals("beIN SPORTS HD 1 [TR]", AceStreamChannels.byId("bein-sports-hd-1-tr")?.name)
        assertEquals(
            "beIN SPORTS HD 2 [TR]",
            AceStreamChannels.byName("bein sports hd 2 [tr]")?.name,
        )
        assertNull(AceStreamChannels.byId("olmayan"))
        assertNull(AceStreamChannels.byName("olmayan"))
    }
}
