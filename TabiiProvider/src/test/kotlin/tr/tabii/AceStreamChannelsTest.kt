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
            assertTrue("${c.id}: content id gecersiz: ${c.contentId}", AceStream.isContentId(c.contentId))
            assertTrue("${c.id}: ad bos", c.name.isNotBlank())
            assertTrue("${c.id}: logo bos", !c.logo.isNullOrBlank())
        }
    }

    @Test
    fun `bein sports 1 kullanicinin verdigi content id ile listeleniyor`() {
        val channel = AceStreamChannels.ALL.firstOrNull { it.name == "beIN SPORTS 1" }
        assertNotNull(channel)
        assertEquals("118416583faa3fb4f03800173429b6b844b43a52", channel!!.contentId)
        assertEquals("acestream://118416583faa3fb4f03800173429b6b844b43a52", channel.pageUrl)
    }

    @Test
    fun `content id ile kanal bulma`() {
        val channel = AceStreamChannels.byContentId("118416583faa3fb4f03800173429b6b844b43a52")
        assertEquals("beIN SPORTS 1", channel?.name)
        // buyuk/kucuk harf fark etmemeli
        assertNotNull(AceStreamChannels.byContentId("118416583FAa3FB4F03800173429B6B844B43A52"))
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
