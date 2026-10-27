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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tr.common.NextData

/**
 * tabii canlı TV kanalları için Cloudstream 3 provider'ı.
 *
 * Kanal listesi sunucu tarafında sayfaya gömülüyor: her canlı kanal sayfasının
 * `__NEXT_DATA__` bloğu tüm kanalları ve medya adreslerini içeriyor. Bu yüzden
 * "tohum" olarak tek bir kanal sayfasını kullanıp listeyi oradan okuyoruz.
 *
 * Bunun yanında doğrudan HLS ile yayınlanan spor kanalları (beIN SPORTS,
 * S Sport, Tivibu Spor) [DirectStreams] üzerinden eklenir; tabii'de bulunmayan
 * bu kanallar kendi CDN'lerinden oynatılır.
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

        // Tabii'nin kanal listesi saatlerce degismez; 5 dakika her acilista
        // gereksiz indirme + parse uretiyordu. 15 dakika tazelik ile gider
        // (elle yenileme search(allowStale=false) yine taze ceker).
        const val CACHE_TTL_MS = 15 * 60 * 1000L

        /** Ana sayfadaki doğrudan HLS spor kanalları bölümünün adı. */
        const val SPORTS_SECTION = "Spor Kanalları"
    }

    private val seedUrl get() = "$mainUrl/tr/watch/live/$SEED_SLUG?trackId=$SEED_TRACK_ID"

    override val mainPage = mainPageOf(
        seedUrl to "Canlı TV"
    )

    private data class Catalog(
        val channels: List<TabiiChannel>,
        val byUrl: Map<String, TabiiChannel>,
        val fetchedAt: Long,
    ) {
        fun isFresh(now: Long): Boolean = now - fetchedAt < CACHE_TTL_MS
    }

    @Volatile
    private var catalog: Catalog? = null

    /** Aynı anda birden fazla sayfa indirmesini engeller. */
    private val fetchMutex = Mutex()

    /**
     * Kanal listesini çeker. Kısa süreli önbellek tutulur, çünkü arama ve
     * ana sayfa her çağrıda aynı listeye ihtiyaç duyuyor.
     *
     * Önbellek süresi dolduğunda liste tek sefer yenilenir ([fetchMutex]);
     * indirme başarısız olursa son başarılı liste korunur. Böylece geçici bir
     * ağ sorunu ana sayfayı boşaltmaz.
     *
     * @param allowStale `true` ise süresi dolmuş önbellek yenilenmeden
     *   döndürülür (hızlı arama).
     */
    private suspend fun fetchChannels(allowStale: Boolean = false): List<TabiiChannel> {
        val now = System.currentTimeMillis()
        val snapshot = catalog
        if (snapshot != null && (allowStale || snapshot.isFresh(now))) return snapshot.channels

        return fetchMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            val current = catalog
            if (current != null && current.isFresh(lockedNow)) {
                current.channels
            } else {
                refresh()?.channels ?: current?.channels ?: emptyList()
            }
        }
    }

    /** Sayfayı indirir, kanalları çıkarır ve önbelleğe yazar; başarısızsa `null`. */
    private suspend fun refresh(): Catalog? {
        // runCatching CancellationException'i da yutardı; dis kapsam iptal edildiginde
        // iptalin yayilmasi icin try/catch ile ayirt edilir (DirectStreams ile ayni desen).
        val html = try {
            app.get(seedUrl, referer = "$mainUrl/").text
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val tree = NextData.tree(html) ?: return null
        // Logolar sayfada yalnizca dosya adi olarak duruyor; mutlak adres bu
        // taban adresle olusuyor.
        val imageBase = NextData.node(tree, "props", "config", "image_cdn_path")
            ?.asText()?.takeIf { it.isNotBlank() }
        val array = NextData.node(tree, "props", "pageProps", "liveChannels") ?: return null
        val channels = array.mapNotNull { it.toTabiiChannel(imageBase) }
        if (channels.isEmpty()) return null

        val fresh = Catalog(
            channels = channels,
            byUrl = buildMap {
                for (channel in channels) {
                    // `load()` iki adres biçimini de kabul eder: tam sayfa
                    // adresi ve slug.
                    put(channel.pageUrl, channel)
                    put(channel.slug, channel)
                }
            },
            fetchedAt = System.currentTimeMillis(),
        )
        catalog = fresh
        return fresh
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels()
        // Her iki satir da yatay kartlarla cizilir; [LogoAssets] logolari
        // kare tuvale sigidigi icin kart orani degisse de logo kirilmaz.
        //
        // Dogrudan HLS kanallari [DirectStreams.CHANNELS] tek kaynagindan
        // gelir. Buradaki adresteki makine yalnizca tanim amaclidir;
        // asil cozumleme `load()` ve `loadLinks()` icinde [DirectStreams.workingHost]
        // ile yapilir, boylece ana sayfa hic ek istek atmaz.
        val sportsResults = DirectStreams.CHANNELS.map { it.toSearchResult() }
        return newHomePageResponse(
            listOf(
                HomePageList(request.name, channels.map { it.toSearchResult() }, true),
                HomePageList(SPORTS_SECTION, sportsResults, true),
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> =
        search(query, allowStale = false)

    /** Hızlı aramada süresi dolmuş önbellek yenilenmez. */
    override suspend fun quickSearch(query: String): List<SearchResponse> =
        search(query, allowStale = true)

    private suspend fun search(query: String, allowStale: Boolean): List<SearchResponse> {
        val tabiiResults = fetchChannels(allowStale)
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
        // Dogrudan HLS kanallari da aranabilir; adresler sabit tanim
        // amaclidir, makine `load()` icinde cozulur.
        val sportsResults = DirectStreams.CHANNELS
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
        return tabiiResults + sportsResults
    }

    override suspend fun load(url: String): LoadResponse {
        // Direct HLS spor kanallari (Beinsports / S Sport / Tivibu serverlari).
        // Kanal tanimi yalnizca YOL uzerinden eslesir; makine adi
        // engelleme sonucu degisebilecegi icin dikkate alinmaz. Burada o
        // anki calisan makine bulunur ve oynatilabilir adres o makineye
        // gosterilir.
        DirectStreams.channelByUrl(url)?.let { channel ->
            val host = DirectStreams.workingHost(channel)
            val stream = DirectStreams.urlFor(host, channel.path)
            return newLiveStreamLoadResponse(channel.title, url, stream) {
                posterUrl = LogoAssets.sportsPosterFor(channel.logoKey, null)
                plot = "${channel.title} — doğrudan HLS yayını"
            }
        }

        val channel = channelByUrl(url)
            ?: throw ErrorLoadingException("Kanal bulunamadı: $url")

        val media = channel.playableMedia
            ?: throw ErrorLoadingException("${channel.title} için oynatılabilir yayın bulunamadı")

        return newLiveStreamLoadResponse(channel.title, channel.pageUrl, media.url) {
            posterUrl = LogoAssets.tabiiPosterFor(channel.slug, channel.logo)
            plot = if (channel.requiresLogin) {
                "${channel.title} — tabii Premium kanalları"
            } else {
                "${channel.title} — canlı yayın"
            }
        }
    }

    /**
     * Adrese göre kanal bulur.
     *
     * Katalog varsa yalnızca bellekte aranır; oynatma sırasında önbellek
     * süresi dolsa bile ağa çıkılmaz.
     */
    private suspend fun channelByUrl(url: String): TabiiChannel? {
        catalog?.let { return it.byUrl[url] }
        fetchChannels()
        return catalog?.byUrl?.get(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Dogrudan HLS kanallari: oynatma baslamadan hemen once makineyi
        // bir kez daha dogrular (load ile loadLinks arasinda makine
        // olmus olabilir) ve adresi guncel makineye gosterir.
        DirectStreams.channelByUrl(data)?.let { channel ->
            val host = DirectStreams.workingHost(channel)
            val stream = DirectStreams.urlFor(host, channel.path)
            callback.invoke(
                newExtractorLink(name, channel.title, stream) {
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
            posterUrl = LogoAssets.tabiiPosterFor(slug, logo)
        }
    }

    private fun DirectStreams.Channel.toSearchResult(): SearchResponse {
        return newLiveSearchResponse(
            title,
            // Makine adi tanim amaclidir; `load()`/`loadLinks()` icinde asil
            // cozumleme [DirectStreams.workingHost] ile yapilir.
            DirectStreams.urlFor(DirectStreams.PRIMARY_HOST, path),
            TvType.Live,
        ) {
            posterUrl = LogoAssets.sportsPosterFor(logoKey, null)
        }
    }
}
