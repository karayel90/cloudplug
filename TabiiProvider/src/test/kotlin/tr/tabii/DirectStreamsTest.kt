package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectStreamsTest {

    @Test
    fun `kanal kimlikleri benzersiz`() {
        val ids = DirectStreams.CHANNELS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `on iki kanal var ve basliklar benzersiz`() {
        assertEquals(12, DirectStreams.CHANNELS.size)
        val titles = DirectStreams.CHANNELS.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `her kanalin yolu checklist altinda ve m3u8 ile biter`() {
        for (c in DirectStreams.CHANNELS) {
            assertTrue("${c.id}: yol bos", c.path.isNotBlank())
            assertTrue("${c.id}: yol checklist altinda degil", c.path.startsWith("checklist/"))
            assertTrue("${c.id}: yol m3u8 ile bitmiyor", c.path.endsWith(".m3u8"))
        }
    }

    @Test
    fun `her kanalin logo anahtari LogoAssets tarafindan taninir`() {
        // Logo anahtari taninmiyorsa kartta logo gorunmez.
        for (c in DirectStreams.CHANNELS) {
            assertNotNull(
                "${c.id}: logo anahtari taninmiyor: ${c.logoKey}",
                LogoAssets.acePosterFor(c.logoKey, null),
            )
        }
    }

    @Test
    fun `hostNumber kalibi dogru calisir`() {
        assertEquals(217, DirectStreams.hostNumber("andro.evrenesoglu217.click"))
        assertEquals(1, DirectStreams.hostNumber("andro.evrenesoglu1.click"))
        assertNull(DirectStreams.hostNumber("ornek.com"))
        assertNull(DirectStreams.hostNumber("andro.evrenesoglu.click"))
    }

    @Test
    fun `aday listesi birincil makineyle baslar ve benzersizdir`() {
        val hosts = DirectStreams.candidateHosts("andro.evrenesoglu217.click")
        assertEquals("andro.evrenesoglu217.click", hosts.first())
        assertEquals("yinelenen aday", hosts.size, hosts.toSet().size)
    }

    @Test
    fun `aday listesi tarama yaricapini kapsar`() {
        val hosts = DirectStreams.candidateHosts("andro.evrenesoglu217.click")
        val r = DirectStreams.SWEEP_RADIUS
        for (n in (217 - r)..(217 + r)) {
            assertTrue("aday eksik: $n", hosts.contains("andro.evrenesoglu$n.click"))
        }
    }

    @Test
    fun `kaliba uymayan makinede aday yalnizca kendisidir`() {
        val hosts = DirectStreams.candidateHosts("ornek.com")
        assertEquals(listOf("ornek.com"), hosts)
    }

    @Test
    fun `channelByUrl farkli makine adlariyla eslesir`() {
        val channel = DirectStreams.channelByUrl(
            "https://andro.evrenesoglu999.click/checklist/androstreamlivebs1.m3u8",
        )
        assertNotNull(channel)
        assertEquals("bein1", channel!!.id)
    }

    @Test
    fun `channelByUrl tum kanal yollarini tanir`() {
        for (c in DirectStreams.CHANNELS) {
            val url = DirectStreams.urlFor("andro.evrenesoglu217.click", c.path)
            assertEquals("${c.id}: adres taninmadi", c, DirectStreams.channelByUrl(url))
        }
    }

    @Test
    fun `channelByUrl bilinmeyen adreste null doner`() {
        assertNull(DirectStreams.channelByUrl("https://ornek.com/bilinmeyen.m3u8"))
        assertNull(DirectStreams.channelByUrl("https://www.tabii.com/tr/watch/live/trt1"))
    }

    @Test
    fun `isPlaylist yalnizca m3u8 icerigini kabul eder`() {
        assertTrue(DirectStreams.isPlaylist("#EXTM3U\n#EXT-X-VERSION:3\n"))
        assertTrue(DirectStreams.isPlaylist("  #EXTM3U"))
        assertFalse(DirectStreams.isPlaylist(""))
        assertFalse(DirectStreams.isPlaylist("<html>404 Not Found</html>"))
        assertFalse(DirectStreams.isPlaylist("#EXT-X-TARGETDURATION:6"))
    }

    @Test
    fun `birincil makine adaylarin icinde`() {
        val hosts = DirectStreams.candidateHosts(DirectStreams.PRIMARY_HOST)
        assertTrue(hosts.contains(DirectStreams.PRIMARY_HOST))
    }

    @Test
    fun `tercih edilen makineler aday listesine girer`() {
        val far = "andro.evrenesoglu" + (300 + DirectStreams.SWEEP_RADIUS * 2) + ".click"
        val hosts = DirectStreams.candidateHosts(far)
        // Tercih listesi numara mesafesine gore siralansa da listeye girer.
        for (h in DirectStreams.PREFERRED_HOSTS) {
            assertTrue("tercihli aday eksik: $h", hosts.contains(h))
        }
    }
}
