package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Test
import tr.common.M3u

class LogoAssetsTest {

    @Test
    fun `bilinen kanal icin kendi barindirdigimiz poster doner`() {
        assertEquals(
            "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/atv.png",
            LogoAssets.posterFor("atv", "https://ornek.com/atv.png"),
        )
    }

    @Test
    fun `normalize edilmis ad anahtar olarak kullanilir`() {
        // Katalogdaki başlık "TRT 1 (1080p)"; anahtar normalize edilmiş ad.
        assertEquals("trt1", M3u.normalizeName("TRT 1 (1080p)"))
        assertEquals(
            "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/trt1.png",
            LogoAssets.posterFor(M3u.normalizeName("TRT 1 (1080p)"), null),
        )
    }

    @Test
    fun `hazir logosu olmayan kanal ham adrese duser`() {
        // M3U'ya sonradan eklenen kanallar için logo üretilmemiş olabilir.
        assertEquals(
            "https://ornek.com/yeni.png",
            LogoAssets.posterFor("yenikanal", "https://ornek.com/yeni.png"),
        )
        assertEquals(null, LogoAssets.posterFor("yenikanal", null))
    }

    @Test
    fun `tabii kanali slug ile kendi deposundan poster alir`() {
        assertEquals(
            "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/tabii/trt1.png",
            LogoAssets.tabiiPosterFor("trt1", "https://cms-tabii-public-image.tabii.com/int/22938.png"),
        )
        assertEquals(
            "https://ornek.com/yeni.png",
            LogoAssets.tabiiPosterFor("yeni-kanal", "https://ornek.com/yeni.png"),
        )
    }

    @Test
    fun `spor kanali kimligi ile kendi deposundan poster alir`() {
        assertEquals(
            "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/sports/bein-sports-1-tr.png",
            LogoAssets.sportsPosterFor("bein-sports-1-tr", null),
        )
        assertEquals(null, LogoAssets.sportsPosterFor("yeni-kanal", null))
    }
}
