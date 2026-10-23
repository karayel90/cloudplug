package tr.tabii

import com.lagradost.cloudstream3.app
import tr.common.M3u

/**
 * M3U listesinden beslenen Türkçe canlı kanal kataloğu.
 *
 * ## Neden sabit katalog yerine liste çekme?
 *
 * Yayın adresleri yayıncıların CDN değişiklikleriyle sık sık bozulur; sabit
 * bir katalog her kırılmanın yeni sürüm çıkarmayı gerektirir. [PLAYLIST_URL]
 * topluluk tarafından güncellenen bir M3U listesidir: eklenti listeyi
 * çalışma anında çeker, çift kanalları temizler ve her kanal için en yüksek
 * çözünürlüklü kaydı kullanır. Böylece adres değişiklikleri eklenti güncellemesi
 * olmadan otomatik izlenir.
 *
 * ## Çift kanal temizliği
 *
 * Listede aynı kanal birden fazla çözünürlükte bulunabilir (örn. "ATV (360p)"
 * ve "ATV (1080p)"). [M3u.dedupeByBestResolution] düzleştirilmiş ada göre
 * eşleştirip en yüksek çözünürlüklü kaydı bırakır; böylece CS3 arama
 * sonuçlarında aynı kanal iki kez görünmez.
 *
 * ## Sıralama
 *
 * Liste çekildikten sonra kanallar yaklaşık izlenme sırasına göre dizilir
 * ([POPULARITY]); böylece ana sayfada ve aramada en çok izlenen ulusal
 * kanallar üstte görünür. Sıralamada bulunmayan kanallar listenin sonuna,
 * kendi (alfabetik) sıralarıyla eklenir.
 *
 * ## Referer stratejisi
 *
 * Bazı CDN'ler (özellikle Daion) isteği `Referer` başlığı olmadan reddeder.
 * M3U listesi yalnızca bazı kanallar için `referrer` taşıdığından, bilinen
 * Daion kanalları için [REFERERS] haritası yedek olarak kullanılır:
 * önce listedeki değer, yoksa bilinen kanalın kendi sitesi.
 */
object TrLiveChannels {

    /** Türkçe kanalların topluluk tarafından korunan M3U listesi. */
    const val PLAYLIST_URL =
        "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye-iptv-org.m3u"

    /** Kanal listesi önbellek süresi. Arama ve ana sayfa aynı listeyi kullanır. */
    private const val CACHE_TTL_MS = 5 * 60_000L

    /**
     * Bilinen Daion CDN kanallarının referer'ları.
     *
     * Daion, `Referer` başlığı olmadan isteği reddediyor (eski TrLiveProvider
     * kanallarında ölçülmüş davranış). M3U listesi bu başlıkları taşımaz;
     * kanalın kendi sitesi yedek olarak gönderilir.
     */
    private val REFERERS = mapOf(
        "/tv8/" to "https://www.tv8.com.tr/",
        "/startv/" to "https://www.startv.com.tr/",
        "/dmax/" to "https://www.dmax.com.tr/",
        "/ntv/" to "https://www.ntv.com.tr/",
        "/kanald/" to "https://www.kanald.com.tr/",
        "/haberturktv/" to "https://www.haberturk.com/",
    )

    /**
     * Kanalların ana sayfadaki sırası — yaklaşık izlenme (reyting) sırası.
     *
     * Canlı reyting verisi sağlayan ücretsiz bir API olmadığı için sıralama
     * editoryaldir: ulusal yayıncıların prime-time izlenme payları ve haber
     * kanallarının gün içi erişimi gözetilerek en çok izlenenden en aza doğru
     * dizilmiştir. Liste tek yerden yönetilir; bir kanalı öne almak için
     * düzleştirilmiş adını (`M3u.normalizeName`) buraya taşımak yeterlidir.
     *
     * Burada olmayan kanallar sıralamanın sonuna, M3U listesindeki kendi
     * sıralarıyla (alfabetik) eklenir.
     */
    private val POPULARITY: List<String> = listOf(
        // Ulusal genel içerik yayıncıları
        "atv", "nowtv", "kanald", "startv", "tv8", "trt1", "showtv",
        // Haber
        "trthaber", "haberturktv", "ntv", "ahaber", "halktv", "tv100",
        "tele1", "24tv", "haberglobal", "flashhabertv", "ulketv", "akittv",
        "tvnet", "tytturk", "bloomberght",
        // Spor
        "trtspor", "ssport", "ssport2", "beinsportshaber", "aspor", "fbtv",
        "sportstv",
        // Genel / eğlence
        "teve2", "kanal7", "beyaztv", "360tv", "tv24", "kanal7avrupa",
        // Çocuk
        "trtcocuk", "minikacocuk", "minikago", "trtdiyanetcocuk",
        "trtebailkokul", "trtebaortaokul", "trtebalise",
        // Belgesel
        "trtbelgesel", "nationalgeographic", "nationalgeographicwild",
        // Müzik
        "trtmuzik", "powerturk", "powerturktv", "kralpoptv", "number1tv",
        // Kamu / uluslararası
        "trt2", "trt3", "trtturk", "trtavaz", "trtarabi", "trtkurd",
        "trtworld", "trtworldhd",
    )

    /** Çözünürlük belirten sonekler; popülerlik eşleşmesinde tolere edilir. */
    private val QUALITY_SUFFIXES = setOf("4k", "8k", "hd", "fhd", "uhd", "sd")

    private var cached: List<M3u.Entry> = emptyList()
    private var cachedAt: Long = 0L

    /**
     * Kanalları popülerlik sırasına dizer.
     *
     * Sıralama kararlıdır (`sortedBy`): eşit puanlı — yani [POPULARITY]
     * listesinde bulunmayan — kanallar M3U listesindeki kendi sıralarını
     * korur. Bu önemli, çünkü liste zaten kabaca alfabetiktir; bilinmeyen
     * kanalların her çekilişte yer değiştirmesi istenmez.
     *
     * "TRT 1 4K" gibi çözünürlük sonekli başlıklar da "trt1" anahtarıyla
     * eşleşir; "ATV Alanya" ise "atv" ile eşleşmez, çünkü sonek bir
     * çözünürlük etiketi değildir.
     */
    fun sortByPopularity(entries: List<M3u.Entry>): List<M3u.Entry> =
        entries.sortedBy { popularityRank(M3u.normalizeName(it.title)) }

    /** Düzleştirilmiş ada göre popülerlik sırası; bilinmeyenler listenin sonunda. */
    private fun popularityRank(normalized: String): Int {
        POPULARITY.forEachIndexed { index, base ->
            if (normalized == base) return index
            val suffix = normalized.removePrefix(base)
            if (normalized.startsWith(base) && suffix in QUALITY_SUFFIXES) return index
        }
        return POPULARITY.size
    }

    /**
     * Kanal listesini çeker; önbellek doluyorsa ağa çıkmaz.
     *
     * Liste boş dönerse (ağ hatası, ayrıştırma boşluğu) son başarılı önbellek
     * korunur: geçici bir ağ sorunu, kanalların tamamen kaybolmasına yol
     * açmamalı.
     */
    suspend fun channels(): List<M3u.Entry> {
        val now = System.currentTimeMillis()
        if (cached.isNotEmpty() && now - cachedAt < CACHE_TTL_MS) return cached

        val text = runCatching { app.get(PLAYLIST_URL).text }.getOrNull() ?: return cached
        val entries = sortByPopularity(M3u.dedupeByBestResolution(M3u.parse(text)))
        if (entries.isNotEmpty()) {
            cached = entries
            cachedAt = now
        }
        return cached
    }

    /** Yayın adresine göre kanal bulur; `load()` ve `loadLinks()` kullanır. */
    suspend fun byUrl(url: String): M3u.Entry? =
        channels().firstOrNull { it.url == url }

    /**
     * Kanalın oynatma anında gönderilecek referer'ını döndürür.
     *
     * Öncelik M3U listesinin kendi değerindedir (kaynağın bilgisidir); bilinen
     * Daion kanalları için haritadaki değer yedektir.
     */
    fun refererFor(entry: M3u.Entry?): String? {
        entry?.referer?.let { return it }
        val url = entry?.url ?: return null
        if (!url.contains("daioncdn.net")) return null
        return REFERERS.entries.firstOrNull { url.contains(it.key) }?.value
    }
}
