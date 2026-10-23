package tr.tabii

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
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

    override val mainPage = mainPageOf(
        TrLiveChannels.PLAYLIST_URL to "Canlı TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        newHomePageResponse(request.name, TrLiveChannels.channels().map { it.toSearchResult() })

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
        // Arama sonuçlarında adres olarak doğrudan yayın manifest'i kullanılır;
        // load()'a aynı adres gelir.
        val entry = TrLiveChannels.byUrl(url)
            ?: throw ErrorLoadingException("Kanal bulunamadı: $url")

        return newLiveStreamLoadResponse(entry.title, entry.url, entry.url) {
            posterUrl = entry.logo
            plot = buildString {
                append(entry.title)
                entry.group?.let { append(" — $it") }
            }
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
            posterUrl = logo
        }
}
