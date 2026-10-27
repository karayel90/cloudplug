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
 * Ayrıca tabii'de bulunmayan ucretli kanallar (beIN SPORTS) için
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

        // Tabii'nin kanal listesi saatlerce degismez; 5 dakika her acilista
        // gereksiz indirme + parse uretiyordu. 15 dakika tazelik ile gider
        // (elle yenileme search(allowStale=false) yine taze ceker).
        const val CACHE_TTL_MS = 15 * 60 * 1000L

        /** Ana sayfadaki Ace Stream bolumunun adi. */
        const val ACE_SECTION = "Ace Stream"

        /** Log etiketi. */
        const val TAG = "TabiiProvider"
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
        // iptalin yayilmasi icin try/catch ile ayirt edilir (DirectStreams/AceStream ile
        // ayni desen).
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
        val directResults = DirectStreams.CHANNELS.map { channel ->
            newLiveSearchResponse(
                channel.title,
                DirectStreams.urlFor(DirectStreams.PRIMARY_HOST, channel.path),
                TvType.Live,
            ) {
                posterUrl = LogoAssets.acePosterFor(channel.logoKey, null)
            }
        }
        val aceResults = AceStreamChannels.ALL.map { it.toSearchResult() } + directResults
        return newHomePageResponse(
            listOf(
                HomePageList(request.name, channels.map { it.toSearchResult() }, true),
                HomePageList(ACE_SECTION, aceResults, true),
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
        val aceResults = AceStreamChannels.ALL
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
        // Dogrudan HLS kanallari da aranabilir; adresler sabit tanim
        // amaclidir, makine `load()` icinde cozulur.
        val directResults = DirectStreams.CHANNELS
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { channel ->
                newLiveSearchResponse(
                    channel.title,
                    DirectStreams.urlFor(DirectStreams.PRIMARY_HOST, channel.path),
                    TvType.Live,
                ) {
                    posterUrl = LogoAssets.acePosterFor(channel.logoKey, null)
                }
            }
        return tabiiResults + aceResults + directResults
    }

    override suspend fun load(url: String): LoadResponse {
        // Direct HLS channels in Ace Stream section (Beinsports servers).
        // Kanal tanimi yalnizca YOL uzerinden eslesir; makine adi
        // engelleme sonucu degisebilecegi icin dikkate alinmaz. Burada o
        // anki calisan makine bulunur ve oynatilabilir adres o makineye
        // gosterilir.
        DirectStreams.channelByUrl(url)?.let { channel ->
            val host = DirectStreams.workingHost(channel)
            val stream = DirectStreams.urlFor(host, channel.path)
            return newLiveStreamLoadResponse(channel.title, url, stream) {
                posterUrl = LogoAssets.acePosterFor(channel.logoKey, null)
                plot = "${channel.title} — doğrudan HLS yayını"
            }
        }

        // Ace Stream kanallari `acestream://<content-id>` adresiyle gelir.
        AceStream.contentIdOf(url)?.let { return loadAceStreamChannel(it) }

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

    /**
     * Bir Ace Stream kanalini yukler.
     *
     * Akis sirasi:
     * 1. **Hizli yol:** bu kanal icin onceden dogrulanmis calisan bir
     *    referans onbellekte varsa yalnizca o tek salt-okunur istekle
     *    kontrol edilir; tam arama + tarama yapilmaz.
     * 2. **Motor hazirligi:** motor kapaliysa Ace Stream uygulamasi
     *    bastirilir ve motorun HTTP ucu ustel bekleme ile beklenir.
     * 3. **Aday cozumleme:** kanalin bilinen content_id'leri (kullanicinin
     *    dogruladigi degerler), sonra motorun arama dizinindeki
     *    infohash'ler denenir. Motor `?content_id=` ile yalnizca content_id,
     *    `?infohash=` ile yalnizca infohash kabul ediyor; bu yuzden turun
     *    degisikligi onemlidir.
     * 4. **Dogrulama:** SALT-OKUNUR bir API ile yapilir (oynatma oturumu
     *    acmaz); ilk adaylar eszamanli, kalanlari sirali denenir.
     * 5. **Devir:** Ace Stream uygulamasi semayi isleyebiliyorsa oynatma
     *    devredilir; devir DOGRULANMIS calisan referansin degeriyle
     *    yapilir, URL'deki sabit kimlik yerine uygulama dogrulanmis
     *    degerle acilir. Uygulama yoksa motorun HTTP ucu CS3'e verilir.
     */
    private suspend fun loadAceStreamChannel(contentId: String): LoadResponse {
        val known = AceStreamChannels.byContentId(contentId)
        val cacheKey = known?.id ?: contentId

        // 1) Hizli yol: onceki yuklemede dogrulanmis calisan ref.
        val cachedRef = AceStream.workingRef(cacheKey)
        if (cachedRef != null) {
            Log.d(TAG, "$cacheKey: önbellekteki çalışan referans deneniyor")
            if (AceStream.checkContent(cachedRef) == null) {
                return finishAceStreamChannel(known, contentId, cachedRef)
            }
            // Önbelleğe yazılan referans artık çalışmiyor; tam taramaya düşülür.
            Log.d(TAG, "$cacheKey: önbellekteki referans çalışmiyor; tam taranacak")
            AceStream.forgetWorkingRef(cacheKey)
        }

        // 2) Motor kapaliysa uygulamayi baslat ve ayaklanmasini bekle.
        ensureAceStreamEngine(contentId)

        val title = known?.name
            ?: AceStream.describe(contentId)
            ?: "Ace Stream"

        val refs = known
            ?.let { AceStream.resolveContentRefs(it.searchNames, it.contentIds) }
            ?: listOf(AceContentRef(contentId))
        Log.d(TAG, "$title: ${refs.size} içerik referansı denenecek")

        val (working, diagnostic) = AceStream.workingAndError(refs)
        if (working == null) {
            throw ErrorLoadingException(
                "$title için yayın bulunamadı. ${diagnostic.orEmpty()} " +
                    "Ace Stream motoru P2P ağına bağlanamıyor olabilir; uygulamayı açıp " +
                    "kanalın yayında olduğundan emin olun."
            )
        }
        AceStream.rememberWorkingRef(cacheKey, working)
        return finishAceStreamChannel(known, contentId, working)
    }

    /**
     * Motor kapaliysa Ace Stream uygulamasini baslatir ve motorun ayaga
     * kalkmasini bekler.
     *
     * Uygulama kurulu degilse (ya da semayi isleyen bir sey bulunmuyorsa)
     * eski anlasilir hata mesaji doner ve akis sona erer; uygulama
     * baslatilir ama motor beklenen surede ayaklanmamissa "tekrar
     * dokunun" mesaji
     * verir; motor ayaklanirsa akis ayni yuklemede surer.
     */
    private suspend fun ensureAceStreamEngine(contentId: String) {
        if (AceStream.isEngineRunning()) return

        val context = AceStreamApp.cs3Context()
        val uri = AceStream.acestreamUrl(contentId)
        if (context == null || !AceStreamApp.canHandle(context, uri)) {
            throw ErrorLoadingException(
                "Ace Stream motoru çalışmıyor. Cihazda Ace Stream uygulamasını açıp " +
                    "kanalın yayında olduğundan emin olun (motor yalnızca uygulama " +
                    "açıkken ayakta kalır)."
            )
        }

        // Once notr baslatma niyeti (uygulamayi acar, oturum acmaz); o
        // bulunamazsa acestream:// semasi: hem motoru kaldirir hem kanali
        // uygulamanin kendi ekraninda acar.
        val launched = AceStreamApp.launchEngine(context) || AceStreamApp.launch(context, contentId)
        if (!launched) {
            Log.w(TAG, "Ace Stream uygulaması başlatılamadı")
            throw ErrorLoadingException(
                "Ace Stream motoru çalışmıyor ve uygulama başlatılamadı. Ace Stream " +
                    "uygulamasını açıp kanala tekrar dokunun."
            )
        }

        if (!AceStream.awaitEngine()) {
            throw ErrorLoadingException(
                "Ace Stream uygulaması açıldı; motor ${(AceStream.AWAIT_ENGINE_MS / 1000)} saniye " +
                    "içinde hazır olmadı. Birkaç saniye bekleyip kanala tekrar dokunun."
            )
        }
    }

    /**
     * Dogrulanmis calisan referansla yukleme yaniti kurar.
     *
     * Ace Stream uygulamasi semayi isleyebiliyorsa oynatmayi uygulamaya
     * devir eder; devirde URL'deki sabit kimlik yerine DOGRULANMIS
     * calisan referansin degeri kullanilir (acestream:// hem content_id
     * hem infohash kabul eder). Boylece sabit kimlik eskimis olsa bile
     * uygulama guncel calisan yayini acar.
     */
    private suspend fun finishAceStreamChannel(
        known: AceStreamChannel?,
        contentId: String,
        working: AceContentRef,
    ): LoadResponse {
        val title = known?.name
            ?: AceStream.describe(working.value, working.isInfohash)
            ?: "Ace Stream"

        // Ace Stream uygulamasi varsa P2P oynatmayi ona birak: motorun TEK
        // oynaticisi var ve CS3 hem onizleme hem oynatma icin ayri istek
        // atinca motor iki oturuma birden acik kalamiyor (HTTP 500).
        val context = AceStreamApp.cs3Context()
        if (context != null && AceStreamApp.canHandle(context, AceStream.acestreamUrl(contentId))) {
            if (AceStreamApp.launch(context, working.value)) {
                return newLiveStreamLoadResponse(
                    title,
                    AceStream.acestreamUrl(contentId),
                    "",
                ) {
                    posterUrl = known?.let { LogoAssets.acePosterFor(it.id, it.logo) }
                    plot = "$title — Ace Stream uygulamasında açılıyor…"
                }
            }
        }

        return newLiveStreamLoadResponse(
            title,
            AceStream.acestreamUrl(contentId),
            working.manifestUrl(),
        ) {
            posterUrl = known?.let { LogoAssets.acePosterFor(it.id, it.logo) }
            plot = "$title — Ace Stream (P2P)"
        }
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
            //
            // DENEYSEL: Link M3U8 yerine VIDEO isaretlenir. CS3 bir HLS
            // adresi gordugunde oynaticiyi acmadan once arka planda kucuk
            // bir onizleme istegi atiyor (PreviewImgM3u8); motor ikinci
            // manifest istegini ikinci oturum sanip HTTP 500 ile kapaniyor.
            // VIDEO isaretlemesi onizleme yukleyicisini devreden cikarir ve
            // manifest TEK istekte cekilir. Uygulamanin Ace Player'i kurulu
            // olan cihazlari etkilemez (asıl yol uygulama devri).
            callback.invoke(
                newExtractorLink(name, name, data) {
                    type = ExtractorLinkType.VIDEO
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
            // `id` burada SearchResponse alanına gölgelenir; kanalın kimliğini
            // açıkça alıyoruz.
            posterUrl = LogoAssets.acePosterFor(this@toSearchResult.id, logo)
        }
    }
}
