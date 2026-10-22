package tr.tabii

import org.junit.Assert.assertEquals
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
    fun `bein sports 1 kullanicinin verdigi content id listede`() {
        val channel = AceStreamChannels.ALL.firstOrNull { it.name == "beIN SPORTS 1" }
        assertNotNull(channel)
        assertTrue(
            "kullanici content ID'si kaybolmus",
            channel!!.contentIds.contains("118416583faa3fb4f03800173429b6b844b43a52"),
        )
        // Birincil ID guncel olan olmali; kullanicinin ID'si yedek olarak durur.
        assertEquals("016d48fb89bb9505ab3f883db1bfb3a7c0a3eccc", channel.contentId)
        assertEquals("acestream://016d48fb89bb9505ab3f883db1bfb3a7c0a3eccc", channel.pageUrl)
    }

    @Test
    fun `content id ile kanal bulma`() {
        val channel = AceStreamChannels.byContentId("118416583faa3fb4f03800173429b6b844b43a52")
        assertEquals("beIN SPORTS 1", channel?.name)
        // buyuk/kucuk harf fark etmemeli
        assertNotNull(AceStreamChannels.byContentId("118416583FAa3FB4F03800173429B6B844B43A52"))
        // birincil olmayan ID de bulunmali
        assertEquals(
            "beIN SPORTS 1",
            AceStreamChannels.byContentId("016d48fb89bb9505ab3f883db1bfb3a7c0a3eccc")?.name,
        )
        assertNull(AceStreamChannels.byContentId("0000000000000000000000000000000000000000"))
    }

    @Test
    fun `kimlik ve ad ile kanal bulma`() {
        assertEquals("beIN SPORTS 1", AceStreamChannels.byId("bein-sports-1")?.name)
        assertEquals("beIN SPORTS 1", AceStreamChannels.byName("bein sports 1")?.name)
        assertNull(AceStreamChannels.byId("olmayan"))
        assertNull(AceStreamChannels.byName("olmayan"))
    }
}
