package tr.tabii

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * ## Ölü yayınlar
 *
 * Liste eski kayıtları da taşır: bazı adresler artık yayın vermez. Katalog
 * oluşturulurken [DeadStreams] içindeki adresler atlanır; böylece kullanıcı
 * açıp hata alacağı kanalları görmez. Filtre, çift kanal temizliğinden önce
 * uygulanır; kanalın ölü 1080p kaydı yerine çalışan 720p kaydı kalır.
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
     * Kanalların ana sayfadaki sırası — editoryal yayın sırası.
     *
     * Canlı reyting verisi sağlayan ücretsiz bir API olmadığı için sıralama
     * editoryaldir: ulusal yayıncıların prime-time izlenme payları, haber
     * kanallarının gün içi erişimi, spor/çocuk kuşakları ve yerel kanalların
     * yayın gücü gözetilerek en çok izlenenden en aza doğru dizilmiştir.
     * Liste tek yerden yönetilir; bir kanalın yerini değiştirmek için
     * düzleştirilmiş adını (`M3u.normalizeName`) satırlar arasında taşımak
     * yeterlidir.
     *
     * Liste katalogdaki tüm kanalları kapsar. İleride M3U'ya yeni bir kanal
     * eklenirse burada olmadığı için sıralamanın sonuna, M3U listesindeki
     * kendi sırasıyla düşer; öne almak için buraya eklenir.
     */
    private val POPULARITY: List<String> = listOf(
        // Genel, eğlence ve sinema
        "trt1", "atv", "nowtv", "kanald", "startv", "tv8", "showtv", "360tv",
        "kanal7", "beyaztv", "dmax", "tlc", "cnbce", "tv2", "fx",
        // Haber, belgesel ve spor
        "a2tv", "ntv", "trtbelgesel", "trthaber", "haberturktv", "ahaber", "bloomberght", "halktv",
        "tele1", "haberglobal", "flashhabertv", "tgrthaber", "trtspor", "aspor", "tv24", "kanal7avrupa",
        // Çocuk
        "trtcocuk", "minikacocuk", "minikago",
        // Müzik
        "powerturk", "powerturktv", "powertv", "number1tv", "powerdance", "powerlove", "number1damar", "number1ask",
        "number1dance", "powerturktaptaze", "powerturkakustik", "powerturkslow", "trtmuzik",
        // TRT ve uluslararası
        "trtdiyanetcocuk", "disneyjr", "trtebaortaokul", "trtturk", "trtavaz", "trtarabi", "trtkurd", "trtworldhd",
        "trtworld",
        // Yerel ve tematik
        "4utv", "almahriahtv", "aksutv", "anadolunettv", "alzahratvturkic", "arastv", "alanyapostatv",
        "atvalanya", "benguturktv", "brtv", "bursaastv", "cine1", "denizpostasitv", "ciftcitv", "dha",
        "diyartv", "dosttv", "ertv", "elsharqtv", "estv", "etvkayseri", "diyanettv", "edessatv",
        "dreamturk", "eurod", "fortunatv", "erzurumwebtv", "grandcinema", "finansturktv", "ilketv", "haber61tv",
        "hunattv", "htsportv", "kanal23", "kanal15", "kanal12", "kanal26", "kanal58", "kanalfirat",
        "kanalv", "kaytv", "kanalddrama", "konyaolaytv", "kanal3", "kentturktv", "lalegultv", "lifetv",
        "medmuzik", "mekameleentv", "mavikaradeniz", "meltemtv", "mercantv", "luystv", "satranctv", "sercemtv",
        "sunrtv", "tempotv", "semerkandtv", "tivi6", "tbmmtv", "tontv", "tjktv", "tjktv2",
        "tr24tv", "trtsporyildiz", "tv4", "tv41", "tvden", "urfanatiktv", "tv264", "van65tv",
        "vavtv", "zaroktv",
    )

    /** Çözünürlük belirten sonekler; popülerlik eşleşmesinde tolere edilir. */
    private val QUALITY_SUFFIXES = setOf("4k", "8k", "hd", "fhd", "uhd", "sd")

    /**
     * Düzleştirilmiş kanal adı → editoryal sıra.
     *
     * Eskiden her kayıt için 129 anahtarlık liste baştan taranıyordu
     * (katalog başına ~16.000 karşılaştırma). Katalog küçük olduğu için bu
     * ölçülebilir bir yük değildi; yine de harita ile tek aramaya indirildi.
     */
    private val RANKS: Map<String, Int> =
        POPULARITY.withIndex().associate { (index, key) -> key to index }

    /**
     * Katalogun değişmez anlık görüntüsü.
     *
     * Liste, adres haritası ve zaman damgası tek bir referansta tutulur;
     * böylece eşzamanlı `channels()` / `byUrl()` çağrıları yarım kalmış bir
     * güncelleme görmez.
     */
    private data class Catalog(
        val entries: List<M3u.Entry>,
        val byUrl: Map<String, M3u.Entry>,
        val fetchedAt: Long,
    ) {
        fun isFresh(now: Long): Boolean = now - fetchedAt < CACHE_TTL_MS
    }

    @Volatile
    private var catalog: Catalog? = null

    /** Aynı anda birden fazla katalog indirmesini engeller. */
    private val refreshMutex = Mutex()

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
        entries.sortedBy { popularityRank(it.normalizedTitle) }

    /** Düzleştirilmiş ada göre popülerlik sırası; bilinmeyenler listenin sonunda. */
    private fun popularityRank(normalized: String): Int {
        RANKS[normalized]?.let { return it }
        for (suffix in QUALITY_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                RANKS[normalized.removeSuffix(suffix)]?.let { return it }
            }
        }
        return POPULARITY.size
    }

    /** Testler için: editoryal sıranın düzleştirilmiş anahtarları. */
    internal val rankedKeys: List<String> get() = POPULARITY

    /**
     * Ölü adresleri ayıklar ([DeadStreams]).
     *
     * Filtre **çift kanal temizliğinden önce** uygulanır: böylece bir kanalın
     * en yüksek çözünürlüklü kaydı ölüyse, aynı kanalın çalışan daha düşük
     * çözünürlüklü kaydı seçilebilir. Ters sırada yapılsaydı ölü 1080p kaydı
     * kazanır, ardından filtrelenir ve kanal tamamen kaybolurdu.
     */
    fun withoutDeadStreams(entries: List<M3u.Entry>): List<M3u.Entry> =
        entries.filterNot { it.url in DeadStreams.URLS }

    /**
     * Listede eksik olan doğrulanmış kanalları ekler ([ExtraChannels]).
     *
     * Ek kanallar listenin sonuna eklenir; çift kanal temizliği en yüksek
     * çözünürlüklü kaydı seçerken M3U'daki bir kayıt aynı çözünürlükteyse
     * listedeki kayıt korunur. Böylece liste ileride aynı kanalı daha iyi
     * kalitede yayınlarsa otomatik olarak o kullanılır.
     */
    fun withExtraChannels(entries: List<M3u.Entry>): List<M3u.Entry> =
        entries + ExtraChannels.ENTRIES

    /**
     * Kanal listesini döndürür; önbellek tazeyse ağa çıkmaz.
     *
     * Önbellek süresi dolduğunda liste **tek sefer** yenilenir: ana sayfa,
     * arama ve oynatma aynı anda çağırsa bile [refreshMutex] yalnızca bir
     * indirmeye izin verir. İndirme başarısız olursa son başarılı katalog
     * korunur; geçici bir ağ sorunu kanalların kaybolmasına yol açmamalı.
     *
     * @param allowStale `true` ise süresi dolmuş önbellek yenilenmeden
     *   döndürülür. Hızlı arama (her tuş vuruşunda çalışır) bunu kullanır:
     *   kullanıcı yazarken ağ beklemesi olmaz. Önbellek hiç yoksa yine de
     *   indirme yapılır.
     */
    suspend fun channels(allowStale: Boolean = false): List<M3u.Entry> {
        val now = System.currentTimeMillis()
        val snapshot = catalog
        if (snapshot != null && (allowStale || snapshot.isFresh(now))) return snapshot.entries

        return refreshMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            val current = catalog
            if (current != null && current.isFresh(lockedNow)) {
                current.entries
            } else {
                refresh()?.entries ?: current?.entries ?: emptyList()
            }
        }
    }

    /** Listeyi indirir, temizler ve önbelleğe yazar; başarısızsa `null`. */
    private suspend fun refresh(): Catalog? {
        val text = runCatching { app.get(PLAYLIST_URL).text }.getOrNull() ?: return null
        val parsed = withoutDeadStreams(withExtraChannels(M3u.parse(text)))
        val entries = sortByPopularity(M3u.dedupeByBestResolution(parsed))
        if (entries.isEmpty()) return null
        val fresh = Catalog(
            entries = entries,
            byUrl = entries.associateBy { it.url },
            fetchedAt = System.currentTimeMillis(),
        )
        catalog = fresh
        return fresh
    }

    /**
     * Yayın adresine göre kanal bulur; `load()` ve `loadLinks()` kullanır.
     *
     * Katalog varsa yalnızca bellekte aranır. Bu önemli: yayın açılırken
     * önbellek süresi dolmuşsa ağa çıkmak, kanal değiştirmeyi gereksiz yere
     * geciktirirdi. Adres katalogda yoksa `null` döner (kanal zaten listeden
     * kalkmıştır). Yalnızca hiç katalog yokken indirme yapılır.
     */
    suspend fun byUrl(url: String): M3u.Entry? {
        catalog?.let { return it.byUrl[url] }
        return channels().firstOrNull { it.url == url }
    }

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
