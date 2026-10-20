package tr.tabii

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import tr.common.NextData

/**
 * tabii canlı TV kanalları için Cloudstream 3 provider'ı.
 *
 * Kanal listesi sunucu tarafında sayfaya gömülüyor: her canlı kanal sayfasının
 * `__NEXT_DATA__` bloğu tüm kanalları ve medya adreslerini içeriyor. Bu yüzden
 * "tohum" olarak tek bir kanal sayfasını kullanıp listeyi oradan okuyoruz.
 */
class TabiiProvider : MainAPI() {
    override var mainUrl = "https://www.tabii.com"
    override var name = "tabii"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasQuickSearch = true

    private companion object {
        const val SEED_SLUG = "trt1"
        const val SEED_TRACK_ID = 150002
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val seedUrl get() = "$mainUrl/tr/watch/live/$SEED_SLUG?trackId=$SEED_TRACK_ID"

    override val mainPage = mainPageOf(
        seedUrl to "Canlı TV"
    )

    private var cachedChannels: List<TabiiChannel> = emptyList()
    private var cachedAt: Long = 0L

    /**
     * Kanal listesini çeker. Kısa süreli önbellek tutulur, çünkü arama ve
     * ana sayfa her çağrıda aynı listeye ihtiyaç duyuyor.
     */
    private suspend fun fetchChannels(force: Boolean = false): List<TabiiChannel> {
        val now = System.currentTimeMillis()
        if (!force && cachedChannels.isNotEmpty() && now - cachedAt < CACHE_TTL_MS) {
            return cachedChannels
        }
        val html = app.get(seedUrl, referer = "$mainUrl/").text
        val tree = NextData.tree(html) ?: return emptyList()
        val array = NextData.node(tree, "props", "pageProps", "liveChannels") ?: return emptyList()
        val channels = array.mapNotNull { it.toTabiiChannel() }
        if (channels.isNotEmpty()) {
            cachedChannels = channels
            cachedAt = now
        }
        return channels
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels()
        return newHomePageResponse(request.name, channels.map { it.toSearchResult() })
    }

    override suspend fun search(query: String): List<SearchResponse> =
        fetchChannels()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val channel = fetchChannels().firstOrNull { it.pageUrl == url || it.slug == url }
            ?: throw ErrorLoadingException("Kanal bulunamadı: $url")

        val media = channel.playableMedia
            ?: throw ErrorLoadingException("${channel.title} için oynatılabilir yayın bulunamadı")

        return newLiveStreamLoadResponse(channel.title, channel.pageUrl, media.url) {
            posterUrl = channel.logo
            plot = if (channel.requiresLogin) {
                "${channel.title} — tabii Premium kanalları"
            } else {
                "${channel.title} — canlı yayın"
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Abonelik gerektiren kanallar için kullanıcının tabii oturumu eklenir.
        val session = storedCredentials?.takeIf { it.isNotBlank() }
        callback.invoke(
            newExtractorLink(name, name, data) {
                type = ExtractorLinkType.M3U8
                quality = Qualities.Unknown.value
                referer = "$mainUrl/"
                if (session != null) headers = mapOf("Cookie" to session)
            }
        )
        return true
    }

    private fun TabiiChannel.toSearchResult(): SearchResponse {
        return newLiveSearchResponse(
            title,
            pageUrl,
            TvType.Live,
        ) {
            posterUrl = logo
        }
    }
}
