package tr.tabii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Karşılaştırma: TR Kanalları (`posterFor`) vs Ace Stream (`acePosterFor`)
 * beIN 1/2/3 kanalları için aynı metod mu?
 */
class LogoMethodCompareTest {

    // TR Kanalları metodunu temsil eder (posterFor)
    private fun trMethod(key: String, fallback: String?): String? =
        LogoAssets.posterFor(key, fallback)

    // Ace Stream metodunu temsil eder (acePosterFor)
    private fun aceMethod(key: String, fallback: String?): String? =
        LogoAssets.acePosterFor(key, fallback)

    @Test
    fun `bein kanallari ayni metod mu`() {
        val beinKeys = listOf(
            "bein-sports-hd-1-tr",
            "bein-sports-hd-2-tr",
            "bein-sports-hd-3-tr",
        )
        val fallback = "https://i.ibb.co/3fPVPLL/Bein1.png" // ornek fallback

        for (key in beinKeys) {
            val tr = trMethod(key, fallback)
            val ace = aceMethod(key, fallback)

            // TR metodunu kullaninca (logos/{key}.png) — bu dosya YOK (logos/ace/ altinda)
            // Ace metodunu kullaninca (logos/ace/{key}.png) — bu dosya VAR
            println("=== $key ===")
            println("  TR method  -> $tr")
            println("  Ace method -> $ace")

            // Ace method local PNG vermeli (logolar logos/ace/ altinda)
            assertNotNull("Ace method local logo bulmali: $key", ace)
            assert(ace!!.contains("logos/ace/")) { "Ace logos/ace/ icermeli: $ace" }

            // TR method: eger bein key KEYS set'indeyse lokal, degilse fallback dondurur
            if (tr == fallback) {
                // TR method fallback'a duser (bein key KEYS'te yok)
                println("  TR -> FALLBACK (bein key KEYS set'inde degil)")
            } else {
                // TR method lokal PNG dondurdu (KEYS set'inde var)
                assertNotNull("TR lokal olmali", tr)
                assert(tr!!.contains("logos/")) { "TR logos/ icermeli: $tr" }
                println("  TR -> LOCAL (KEYS set'inde var): $tr")
            }
        }
    }

    @Test
    fun `tr method tr kanallari icin calisiyor`() {
        // TR Kanallari ornegini dogrula
        val tr = trMethod("atv", "https://ornek.com/atv.png")
        assertNotNull(tr)
        assert(tr!!.contains("logos/atv.png")) { "TR logos/atv.png: $tr" }
    }

    @Test
    fun `ace method bein icin dogru local donduruyor`() {
        val ace = aceMethod("bein-sports-hd-1-tr", "https://i.ibb.co/3fPVPLL/Bein1.png")
        assertNotNull("Ace method bein-1 local logo dondurur", ace)
        assert(ace!!.contains("logos/ace/bein-sports-hd-1-tr.png")) {
            "Ace logos/ace/bein-sports-hd-1-tr.png olmali: $ace"
        }
    }
}
