package tr.trlive

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

/**
 * Türkiye'nin büyük ücretsiz yayıncıları için Cloudstream 3 provider'ı.
 *
 * Kanal listesi sabittir ve yayınlar doğrudan Daion CDN'inden HLS olarak
 * sunulur. ATV dışındaki kanalların adresi tek başına yeterlidir; ATV'in
 * yayını `DaionToken` ile imzalanması gereken bir "secure token" istemek
 * zorundadır, bu yüzden oynatma anında çözülür.
 */
class TrLiveProvider : MainAPI() {
    override var mainUrl = "https://www.startv.com.tr"
    override var name = "TR Kanalları"
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "https://www.tv8.com.tr/canli-yayin" to "Canlı TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        newHomePageResponse(request.name, TrLiveChannels.ALL.map { it.toSearchResult() })

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isEmpty()) return TrLiveChannels.ALL.map { it.toSearchResult() }
        return TrLiveChannels.ALL
            .filter { it.name.contains(q, ignoreCase = true) }
            .map { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val channel = TrLiveChannels.byPageUrl(url)
            ?: TrLiveChannels.byId(url)
            // Cloudstream, göreli adresleri `mainUrl + "/" + adres` biçiminde
            // birleştirebiliyor; son çare olarak adresin son parçasına bakıyoruz.
            ?: TrLiveChannels.byId(url.substringAfterLast('/').substringBefore('?'))
            ?: throw ErrorLoadingException("Kanal bulunamadı: $url")

        // ATV gibi kanallarda yayın adresi imzalanmadan açılmıyor; jetonu
        // oynatma anında alıyoruz (geçerlilik ~11,5 saat).
        val streamUrl = if (channel.requiresToken) {
            DaionToken.resolve(channel)
        } else {
            channel.streamUrl
        }

        return newLiveStreamLoadResponse(channel.name, channel.pageUrl, streamUrl) {
            posterUrl = channel.logo
            plot = "${channel.name} — canlı yayın"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val channel = TrLiveChannels.byStreamUrl(data)
        callback.invoke(
            newExtractorLink(name, name, data) {
                type = ExtractorLinkType.M3U8
                quality = Qualities.Unknown.value
                // Daion CDN, referer olmadan isteği reddediyor.
                referer = channel?.referer ?: "https://www.startv.com.tr/"
            }
        )
        return true
    }

    private fun TrLiveChannel.toSearchResult(): SearchResponse =
        newLiveSearchResponse(
            name,
            // Sonuç adresi mutlak olmalı: Cloudstream göreli adresleri
            // `mainUrl + "/" + adres` biçimine çeviriyor.
            pageUrl,
            TvType.Live,
        ) {
            posterUrl = logo
        }
}
