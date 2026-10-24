package tr.tabii

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import tr.common.M3u

/**
 * Topluluk M3U listesinden beslenen Türkçe canlı TV provider'ı.
 *
 * Kanal kataloğu sabit değil: yayın adresleri topluluk tarafından korunan bir
 * M3U listesinden ([TrLiveChannels.PLAYLIST_URL]) çalışma anında çekilir.
 * Liste, aynı kanalın birden fazla çözünürlükteki kayıtlarını içerir; katalog
 * çift kanalları temizler ve her kanalın **en yüksek çözünürlüklü** kaydını
 * kullanır — böylece arama sonuçlarında aynı kanal iki kez görünmez.
 *
 * Yayın adresleri doğrudan oynatılabilir HLS manifestleridir; eski sabit
 * katalogdaki ATV "secure token" akışına gerek kalmamıştır (listedeki ATV
 * kaydı imzasız bir CDN adresidir).
 */
class TrLiveProvider : MainAPI() {
    override var mainUrl = "https://github.com/omerdenizhan/IPTV-M3U"
    override var name = "TR Kanalları"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasQuickSearch = true

    private companion object {
        /**
         * Kanal değiştirme ("zapping") sayfasının adresi.
         *
         * Cloudstream oynatıcısındaki sağ çekmece yalnızca dizi bölümleri
         * için açılır; eklenti API'sinde oynatıcıya doğrudan kanal listesi
         * verme alanı yok. Bu yüzden kanallar bir "dizi"nin bölümleri gibi
         * sunulur: oynatıcıdaki Bölümler çekmecesi kanal listesi olarak
         * çalışır ve seçilen kanal anında açılır.
         */
        const val ZAPPING_URL = "zapping://tr-canli"
        const val ZAPPING_TITLE = "Tüm Kanallar"
        const val ZAPPING_SECTION = "Kanal Değiştir"
    }

    override val mainPage = mainPageOf(
        TrLiveChannels.PLAYLIST_URL to "Canlı TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // `horizontalImages = true` satırı 16:9 yatay kartlara çevirir.
        // Kart görselleri `centerCrop` ile çizildiği için ham logolar (oranları
        // 0.86–4.16 arası) kırpılıyordu; [LogoAssets] logoyu kare tuvale
        // sığdırdığı için satır yatay da olsa dikey de olsa logo tam görünür.
        val channels = TrLiveChannels.channels()
        return newHomePageResponse(
            listOf(
                HomePageList(ZAPPING_SECTION, listOf(zappingCard()), true),
                HomePageList(request.name, channels.map { it.toSearchResult() }, true),
            )
        )
    }

    /**
     * Oynatıcıda kanal değiştirme çekmecesini açan kart.
     *
     * Kart canlı türünde görünür; `load()` bu adres için bir dizi sayfası
     * döndürür ve kanallar o dizinin bölümleri olur.
     */
    private fun zappingCard(): SearchResponse =
        newLiveSearchResponse(
            ZAPPING_TITLE,
            ZAPPING_URL,
            TvType.Live,
            fix = false,
        ) {
            posterUrl = LogoAssets.zappingPoster()
        }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        val channels = TrLiveChannels.channels()
        if (q.isEmpty()) return channels.map { it.toSearchResult() }
        return channels
            .filter { M3u.normalizeName(it.title).contains(M3u.normalizeName(q)) }
            .map { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        // Kanal değiştirme kartı: kanallar bölüm olarak listelenir.
        if (url == ZAPPING_URL) return zappingPage()

        // Arama sonuçlarında adres olarak doğrudan yayın manifest'i kullanılır;
        // load()'a aynı adres gelir.
        val entry = TrLiveChannels.byUrl(url)
            ?: throw ErrorLoadingException("Kanal bulunamadı: $url")

        return newLiveStreamLoadResponse(entry.title, entry.url, entry.url) {
            posterUrl = LogoAssets.posterFor(M3u.normalizeName(entry.title), entry.logo)
            plot = buildString {
                append(entry.title)
                entry.group?.let { append(" — $it") }
            }
        }
    }

    /**
     * Kanalları bir dizinin bölümleri olarak döndürür.
     *
     * Oynatıcı, bölüm listesi olan yayınlarda sağdan açılan "Bölümler"
     * çekmecesini gösterir; burada her bölüm bir kanaldır. Bölüme tıklamak
     * `loadLinks()`'i kanalın yayın adresiyle çağırır ve yayın anında değişir.
     */
    private suspend fun zappingPage(): LoadResponse {
        val channels = TrLiveChannels.channels()
        return newTvSeriesLoadResponse(
            ZAPPING_TITLE,
            ZAPPING_URL,
            TvType.TvSeries,
            channels.mapIndexed { index, entry ->
                newEpisode(entry.url) {
                    name = entry.title
                    season = 1
                    episode = index + 1
                    posterUrl = LogoAssets.posterFor(M3u.normalizeName(entry.title), entry.logo)
                }
            },
        ) {
            posterUrl = LogoAssets.zappingPoster()
            plot = "Tüm Türkçe canlı kanallar. Yayın sırasında oynatıcıdaki " +
                "bölüm çekmecesinden kanal değiştirebilirsiniz."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val entry = TrLiveChannels.byUrl(data)
        callback.invoke(
            newExtractorLink(name, name, data) {
                type = ExtractorLinkType.M3U8
                quality = Qualities.Unknown.value
                // Bazı CDN'ler Referer/User-Agent olmadan isteği reddediyor.
                // Listede belirtilenler önceliklidir; Daion kanalları için
                // bilinen referer yedekte kullanılır (bkz. TrLiveChannels).
                TrLiveChannels.refererFor(entry)?.let { referer = it }
                entry?.userAgent?.let { headers = mapOf("User-Agent" to it) }
            }
        )
        return true
    }

    private fun M3u.Entry.toSearchResult(): SearchResponse =
        newLiveSearchResponse(
            title,
            // Adres mutlak: Cloudstream göreli adresleri mainUrl ile
            // birleştirir; yayın adresleri zaten mutlak olduğu için dokunulmaz.
            url,
            TvType.Live,
        ) {
            posterUrl = LogoAssets.posterFor(M3u.normalizeName(title), logo)
        }
}
