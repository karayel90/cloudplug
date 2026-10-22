package tr.tabii

import android.util.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
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
 *
 * Ayrıca tabii'de bulunmayan ucretli kanallar (beIN SPORTS, Setanta) için
 * **Ace Stream** desteği sunulur. Ace Stream P2P tabanlı bir platformdur ve
 * kanallarını "content ID" ile adresler; yayın, cihazdaki Ace Stream motorunun
 * HTTP ucu üzerinden oynatılır. Bkz. [AceStream].
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

        /** Ana sayfadaki Ace Stream bolumunun adi. */
        const val ACE_SECTION = "Ace Stream"

        /** Log etiketi. */
        const val TAG = "TabiiProvider"
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
        // Logolar sayfada yalnizca dosya adi olarak duruyor; mutlak adres bu
        // taban adresle olusuyor.
        val imageBase = NextData.node(tree, "props", "config", "image_cdn_path")
            ?.asText()?.takeIf { it.isNotBlank() }
        val array = NextData.node(tree, "props", "pageProps", "liveChannels") ?: return emptyList()
        val channels = array.mapNotNull { it.toTabiiChannel(imageBase) }
        if (channels.isNotEmpty()) {
            cachedChannels = channels
            cachedAt = now
        }
        return channels
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels()
        return newHomePageResponse(
            listOf(
                HomePageList(request.name, channels.map { it.toSearchResult() }),
                HomePageList(ACE_SECTION, AceStreamChannels.ALL.map { it.toSearchResult() }),
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val tabiiResults = fetchChannels()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
        val aceResults = AceStreamChannels.ALL
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
        return tabiiResults + aceResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        // Ace Stream kanallari `acestream://<content-id>` adresiyle gelir.
        AceStream.contentIdOf(url)?.let { return loadAceStreamChannel(it) }

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

    /**
     * Bir Ace Stream kanalini yukler.
     *
     * Content ID'ler yayincinin dosyasini yeniden yuklemesiyle degisebiliyor;
     * bayat bir kimlik motor tarafindan kabul edilmez. Bu yuzden once arama
     * dizinindeki guncel ID, sonra listedeki ID denenir ve motorun JSON
     * API'si ile dogrulanir.
     */
    private suspend fun loadAceStreamChannel(contentId: String): LoadResponse {
        // Motor kurulu degilse oynatma hatasi yerine anlasilir bir mesaj
        // gostermek cok daha faydalari.
        if (!AceStream.isEngineRunning()) {
            throw ErrorLoadingException(
                "Ace Stream motoru çalışmıyor. Cihazda Ace Stream Engine kurulu ve " +
                    "açık olmalı (varsayılan adres: ${AceStream.ENGINE_BASE})."
            )
        }

        val known = AceStreamChannels.byContentId(contentId)
        val title = known?.name
            ?: AceStream.describe(contentId)
            ?: "Ace Stream"

        // Once motorun arama dizinindeki guncel ID'ler, sonra sabit yedekler.
        // Ayni kanalin birden fazla yayinci node'u olabiliyor; hepsi denenir.
        val candidates = known
            ?.let { AceStream.resolveContentIds(it.searchNames, it.contentIds) }
            ?: listOf(contentId)
        Log.d(TAG, "$title: ${candidates.size} content ID denenecek")

        // Dogrulama SALT-OKUNUR bir API ile yapilir; oynatma oturumu
        // acilmaz. Aksi halde motorun TEK oynaticisi adaylar arasinda
        // gezinir ve (ayni anda calisan Ace Player dahil) yanlis kanal
        // oynatir.
        var streamId = candidates.firstOrNull() ?: contentId
        for (candidate in candidates) {
            if (AceStream.canLoad(candidate)) {
                streamId = candidate
                break
            }
            Log.d(TAG, "$title: $candidate yüklenemedi, sıradakine geçiliyor")
        }

        return newLiveStreamLoadResponse(title, AceStream.acestreamUrl(contentId), AceStream.manifestUrl(streamId)) {
            posterUrl = known?.logo
            plot = "$title — Ace Stream (P2P)"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Ace Stream yayinlari motorun yerel HTTP ucundan gelir; ek basliga
        // ihtiyac yoktur.
        if (data.startsWith(AceStream.ENGINE_BASE)) {
            // Manifest adresi oldugu gibi gecirilir: motor oynatma oturumunu
            // kendisi acar ve yonlendirmeyi oynatici takip eder.
            //
            // Burada ayrica bir oynatma adresi cozulmez; `format=json` ile
            // her istek bir oturum acar ve motorun TEK oynaticisi adaylar
            // arasinda gezinir. Ayni anda calisan Ace Player'in yayini da
            // bu yuzden kesilir.
            callback.invoke(
                newExtractorLink(name, name, data) {
                    type = ExtractorLinkType.M3U8
                    quality = Qualities.Unknown.value
                }
            )
            return true
        }

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

    private fun AceStreamChannel.toSearchResult(): SearchResponse {
        return newLiveSearchResponse(
            name,
            pageUrl,
            TvType.Live,
            // CS3, arama sonucu adreslerini `MainAPI.fixUrl()` ile
            // mutlaklastirir. `acestream://` "http" ile baslamadigi icin
            // adres `mainUrl` ile birlestirilip bozulur; `fix = false` ile
            // adres oldugu gibi birakilir.
            fix = false,
        ) {
            posterUrl = logo
        }
    }
}
