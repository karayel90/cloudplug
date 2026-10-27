package tr.tabii

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Ace Stream motorunun HTTP arayuzu.
 *
 * Ace Stream, BitTorrent tabanli P2P bir yayin platformudur. Kanallar
 * "content ID" denen 40 haneli onaltilik ozet ile tanimlanir ve
 * `acestream://<content-id>` biciminde adreslenir.
 *
 * CS3 oynaticisi `acestream://` semasini cozemez (`ExtractorLinkType` icinde
 * TORRENT ve MAGNET "No support at the moment" olarak isaretli), bu yuzden
 * yayin motorunun HTTP ucu kullanilir:
 *
 * ```
 * GET http://127.0.0.1:6878/ace/manifest.m3u8?id=<content-id>
 * ```
 *
 * Motor cihaz uzerinde `127.0.0.1:6878` adresinde calisir. CS3 manifestinde
 * `usesCleartextTraffic="true"` oldugu icin bu adrese sorunsuzca ulasilir.
 *
 * @see <a href="https://docs.acestream.net/developers/api-reference/">Ace Stream API</a>
 */
object AceStream {

    /** Ace Stream motorunun varsayilan yerel adresi. */
    const val ENGINE_BASE = "http://127.0.0.1:6878"

    /** HLS manifest ucu. */
    const val MANIFEST_PATH = "/ace/manifest.m3u8"

    /** Arama ucu (Ace Search). */
    const val SEARCH_PATH = "/search"

    /** Servis API ucu. */
    const val SERVICE_PATH = "/server/api"

    /**
     * Motorun durum sorgu adresleri.
     *
     * Belgeler iki farkli uc tarif ediyor; farkli surumler birini ya da
     * digerini uygulamis olabilir. Iki uc da ESZAMANLI sorulur ve ilk
     * basarili yanit kullanilir.
     */
    private val STATUS_URLS = listOf(
        "$ENGINE_BASE$SERVICE_PATH?api_version=3&method=get_status",
        "$ENGINE_BASE/webui/api/service?method=get_version",
    )

    /** Icerik kimligi: 40 haneli onaltilik ozet. */
    private val CONTENT_ID = Regex("\\A[0-9a-fA-F]{40}\\z")

    /** Kanalin yayinda oldugunu bildiren arama sonucu durumu. */
    const val STATUS_AVAILABLE = 2

    /** Motor durum sondajinin zaman asimi. */
    private const val STATUS_TIMEOUT_MS = 3_000L

    /** Salt-okunur icerik dogrulamasinin zaman asimi. */
    private const val CHECK_TIMEOUT_MS = 8_000L

    /** Motorun arama dizinine yapilan istegin zaman asimi. */
    private const val SEARCH_TIMEOUT_MS = 12_000L

    /** Motor durumu onbelleginin gecerlik suresi. */
    private const val ENGINE_STATE_TTL_MS = 10_000L

    /** Motor acilirken beklenecek en fazla sure. */
    const val AWAIT_ENGINE_MS = 20_000L

    /** Motor bekleme dongusunun ilk gecikmesi. */
    private const val AWAIT_STEP_MS = 500L

    /** Motor bekleme dongusunde adim gecikmesinin tavan degeri. */
    private const val AWAIT_STEP_CAP_MS = 3_000L

    /** Calisan referans onbelleginin gecerlik suresi. */
    const val WORKING_REF_TTL_MS = 15 * 60_000L

    /** Bir kerede eszamanli dogrulanacak en fazla aday sayisi. */
    private const val CHECK_PARALLEL_LIMIT = 3

    private val mapper = ObjectMapper()

    /** Verilen metin gecerli bir Ace Stream content ID mi? */
    fun isContentId(value: String): Boolean = CONTENT_ID.matches(value)

    /** Content ID'den `acestream://` adresi uretir. */
    fun acestreamUrl(contentId: String): String = "acestream://$contentId"

    /**
     * `acestream://<id>` adresinden content ID'yi cikarir.
     *
     * CS3, arama sonuclarindaki adresleri `MainAPI.fixUrl()` ile mutlaklastirir.
     * `acestream://` "http" ile baslamadigi icin adres `mainUrl` ile
     * birlestirilir:
     *
     * ```
     * acestream://<id>  ->  https://www.tabii.com/acestream://<id>
     * ```
     *
     * Bu yuzden adresin icinde `acestream://` isareti aranir; boylece ister
     * dogrumdan ister `mainUrl` eklenmis bicim taninir.
     *
     * Gecersiz ya da farkli bir sema iceren adreslerde `null` doner.
     */
    fun contentIdOf(url: String): String? {
        val marker = "acestream://"
        if (!url.contains(marker)) return null
        val raw = url.substringAfter(marker).substringBefore('?').substringBefore('/').trim()
        return raw.takeIf { isContentId(it) }
    }

    // --- Motor durumu: paralel sondaj, kisa sureli onbellek, hazir olma beklemesi ---

    /** En son olculen motor durumu. */
    @Volatile private var engineRunning: Boolean = false

    /** Motor durumunun olculdugu zaman damgasi. */
    @Volatile private var engineProbedAt: Long = 0L

    /**
     * Motorun calisip calismadigini kontrol eder.
     *
     * Motor cihazda kurulu degilse ya da acik degilse `false` doner; boylece
     * kullaniciya "Ace Stream motorunu baslat" gibi anlasilir bir hata
     * gosterebiliriz.
     *
     * Belgeler iki farkli durum ucu tarif ediyor; ikisi de eszamanli
     * sorulur, ilk basarili yanit kazanir. Sonuc kisa sure onbelleklendirilir:
     * ayni ekranda art arda gelen hizli arama + yukleme cagrilari motoru
     * defalarca sondajlamaz.
     *
     * @param forceRefresh true ise onbellek yok sayilir (motor bekleme
     *   dongusu her tur taze sondaj yapar).
     */
    suspend fun isEngineRunning(forceRefresh: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!forceRefresh && isEngineStateFresh(engineProbedAt, now)) return engineRunning
        val running = probeEngine()
        engineProbedAt = now
        engineRunning = running
        return running
    }

    /**
     * Motoru acmak icin bekler; ustel bekleme ile donen sondaj yapar.
     *
     * Ace Stream uygulamasinda motor seferde ~2-4 saniyede ayaga kalkar;
     * bu sureyi kapsayacak sekilde ustel bekleme ilerler.
     */
    suspend fun awaitEngine(maxWaitMs: Long = AWAIT_ENGINE_MS): Boolean {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var stepMs = AWAIT_STEP_MS
        while (System.currentTimeMillis() < deadline) {
            if (isEngineRunning(forceRefresh = true)) return true
            stepMs = nextBackoff(stepMs, AWAIT_STEP_CAP_MS)
            val remaining = deadline - System.currentTimeMillis()
            delay(minOf(stepMs, remaining.coerceAtLeast(1L)))
        }
        return isEngineRunning(forceRefresh = true)
    }

    /**
     * Motorun iki durum ucunu eszamanli sondajlar; bir tek basarili yanit
     * motorun ayakta olduguna yeter.
     */
    private suspend fun probeEngine(): Boolean = coroutineScope {
        STATUS_URLS.map { async { probeStatus(it) } }.awaitAll().any { it }
    }

    private suspend fun probeStatus(url: String): Boolean =
        getBody(url, STATUS_TIMEOUT_MS)?.contains("\"result\"") == true

    /**
     * Motor durum onbellegi henuz saglam mi?
     *
     * Saf yardimci; zaman kaynaklari disaridan verilir ve test edilebilir.
     */
    internal fun isEngineStateFresh(cachedAtMs: Long, nowMs: Long): Boolean =
        cachedAtMs > 0L && nowMs - cachedAtMs < ENGINE_STATE_TTL_MS

    /**
     * Ustel bekleme adimini tavana kadar buyutur.
     *
     * Saf yardimci; motor bekleme dongusu bunu kullanir ve test edilebilir.
     */
    internal fun nextBackoff(currentMs: Long, capMs: Long): Long =
        (currentMs * 2).coerceAtMost(capMs)

    // --- Arama ve referans cozumleme ---

    /**
     * Motorun arama dizininde kanal arar.
     *
     * Motor calismiyorsa veya arama basarisiz olursa bos liste doner.
     */
    suspend fun search(query: String): List<AceStreamResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val url = "$ENGINE_BASE$SEARCH_PATH?query=${URLEncoder.encode(trimmed, "UTF-8")}&page_size=50"
        val body = getBody(url, SEARCH_TIMEOUT_MS) ?: return emptyList()
        return parseSearchResults(body)
    }

    /**
     * Bir kanalin denenecek icerik referanslarini dondurur.
     *
     * Once **sabit (pinned) content_id'ler** gelir: bunlar kullanicinin
     * dogruladigi degerlerdir, dolayisiyla otoritatifdir. Ardindan motorun
     * arama dizininden gelen **infohash'ler** gelir; bunlar `get_content_id`
     * ile content_id'ye cevrilebilir ama gerekmez, cunku motor
     * `?infohash=` parametresini de kabul eder.
     *
     * Ace Stream'de ayni kanalin P2P aginda **birden fazla yayinci
     * (uploader) node'u** olabiliyor ve her birinin kendi kimligi var.
     * Bunlardan biri olurken digeri oluyor; bu yuzden hepsi denenir.
     *
     * Arama sonuclarinda su oncelik kullanilir:
     * 1. Yayinda (status) olma durumu,
     * 2. Hedefte olmayan fazla kelimelerin cezasi (bkz. [extraTokens]),
     * 3. Kullanilabilirlik (availability).
     *
     * @param searchNames Motor dizininde aranacak adlar, oncelik sirasiyla.
     * @param pinned Kanalin bilinen content_id'leri.
     */
    suspend fun resolveContentRefs(
        searchNames: List<String>,
        pinned: List<String>,
    ): List<AceContentRef> {
        val refs = mutableListOf<AceContentRef>()
        // Sabit content_id'ler once.
        pinned.filter { isContentId(it) }.forEach { refs += AceContentRef(it, isInfohash = false) }
        // Arama dizini infohash doner.
        for (query in searchNames) {
            val target = tokens(query)
            if (target.isEmpty()) continue
            search(query)
                .filter { matchesTokens(it.name, target) }
                .sortedWith(searchOrder(target))
                .forEach { refs += AceContentRef(it.contentId, isInfohash = true) }
        }
        return refs.distinctBy { it.value to it.isInfohash }
    }

    /**
     * Arama sonucu siralama kurali: once yayinda olanlar, sonra hedefe
     * fazla kelimesi az olanlar (temiz eslesme), sonra daha kullanilabilir
     * olanlar.
     */
    internal fun searchOrder(target: List<String>): Comparator<AceStreamResult> =
        compareByDescending<AceStreamResult> { it.status == STATUS_AVAILABLE }
            .thenBy { extraTokens(it.name, target) }
            .thenByDescending { it.availability }

    // --- Salt-okunur dogrulama (oynatma oturumu acmaz) ---

    /**
     * Bir icerik referansinin motor tarafindan tanindigini **salt okunur**
     * bir API ile dogrular.
     *
     * Dogrulama icin `get_media_files` kullanilir; bu uc yalnizca icerik
     * bilgisini okur ve **oynatma oturumu acmaz**.
     *
     * Bu onemli: motorun tek bir oynaticisi var ve `format=json` ile her
     * deneme bir oturum acar. Adaylar sirayla denenirken oturumlar
     * birbirinin uzerine biner, hatta ayni anda calisan Ace Player'in
     * yayinini keser. Salt okunur dogrulama bu yuzden oturum acmaz.
     *
     * @return Basarili ise `null`, aksi halde motorun hata mesaji.
     */
    suspend fun checkContent(ref: AceContentRef): String? {
        val body = getBody(ref.checkUrl(), CHECK_TIMEOUT_MS)
            ?: return "içerik kontrol edilemedi"
        val root = runCatching { mapper.readTree(body) }.getOrNull()
            ?: return "içerik kontrol edilemedi"
        val error = root.get("error")
        if (error != null && !error.isNull) {
            return errorMessage(error) ?: "içerik yüklenemedi"
        }
        val result = root.get("result") ?: return "içerik bulunamadı"
        if (result.get("files")?.isEmpty == false) return null
        return "içerik bulunamadı"
    }

    /**
     * Aday referanslari dogrular; calisan ilk referansi dondurur.
     *
     * Ilk birkac aday ESZAMANLI dogrulanir (uc istek salt-okunur oldugu
     * icin oturum acmaz, motoru yormaz) ve sonuclar ONCELIK SIRASINA gore
     * tuketilir: sirada daha oncelikli bir aday calisiyorsa sonrasi
     * goz ardi edilir. Kalan adaylar sirali denenir; boylece uzun aday
     * listelerinde motorun arka yuku kontrol altinda kalir.
     *
     * @return Calisan referans (yoksa `null`) ve ariza tanisi
     *   (basari durumunda `null`).
     */
    suspend fun workingAndError(refs: List<AceContentRef>): Pair<AceContentRef?, String?> {
        if (refs.isEmpty()) return null to null
        val head = refs.take(CHECK_PARALLEL_LIMIT)
        val tail = refs.drop(CHECK_PARALLEL_LIMIT)

        val headResults = coroutineScope {
            head.map { async { it to checkContent(it) } }.awaitAll()
        }
        headResults.firstOrNull { it.second == null }?.let { (ref, _) ->
            return ref to null
        }
        for (ref in tail) {
            if (checkContent(ref) == null) return ref to null
        }
        return null to (headResults.firstOrNull { it.second != null }?.second ?: "içerik bulunamadı")
    }

    // --- Calisan referans onbellegi ---

    /**
     * Kanal anahtari -> dogrulanmis calisan referans onbellegi.
     *
     * Ayni kanal her yuklendiginde arama + tum adaylarin dogrulanmasi
     * yapilmaz: onceden calisan referans biliniyorsa yalnizca o tek
     * salt-okunur istekle kontrol edilir. Referans artik calismiyorsa veya
     * sure dolarsa tam taramaya dusulur.
     */
    private val workingRefs = ConcurrentHashMap<String, Pair<Long, AceContentRef>>()

    /** On bellekten calisan referansi dondurur; sure dolmussa ya da yoksa `null`. */
    fun workingRef(key: String): AceContentRef? {
        val entry = workingRefs[key] ?: return null
        if (!isWorkingRefFresh(entry.first, System.currentTimeMillis())) {
            workingRefs.remove(key, entry)
            return null
        }
        return entry.second
    }

    /** Dogrulanmis calisan referansi onbellege yaz; siradaki yukleme hizli yol. */
    fun rememberWorkingRef(key: String, ref: AceContentRef) {
        workingRefs[key] = System.currentTimeMillis() to ref
    }

    /** Calisan referansi onbellekten siler. */
    fun forgetWorkingRef(key: String) {
        workingRefs.remove(key)
    }

    /**
     * Calisan referans onbelgesi henuz saglam mi?
     *
     * Saf yardimci; zaman kaynaklari disaridan verilir ve test edilebilir.
     */
    internal fun isWorkingRefFresh(cachedAtMs: Long, nowMs: Long): Boolean =
        cachedAtMs > 0L && nowMs - cachedAtMs < WORKING_REF_TTL_MS

    // --- Ad tespiti ---

    /** Motor hata nesnesinden okunabilir bir mesaj cikarir. */
    private fun errorMessage(error: JsonNode): String? {
        if (error.isTextual) return error.asText().takeIf { it.isNotBlank() }
        val message = error.get("message")?.asText()?.takeIf { it.isNotBlank() }
        val code = error.get("code")?.takeIf { it.isNumber }?.asInt()
        return when {
            message != null && code != null -> "$message (kod $code)"
            message != null -> message
            code != null -> "hata kodu $code"
            else -> null
        }
    }

    /**
     * Verilen kimligin kanal adini motorun servis API'sinden sorar.
     *
     * Listede olmayan bir ID ile girildiginde kullanilir. Cihazda olculen
     * davranis:
     *
     * ```
     * ?content_id=<content_id>  ->  calisir
     * ?content_id=<infohash>    ->  HTTP 500
     * ?infohash=<infohash>      ->  calisir
     * ?infohash=<content_id>    ->  HTTP 500
     * ```
     *
     * Degerin turu basta taninmasa da her iki parametre sirayla denenir;
     * yanlisi HTTP 500 doner, dogrusu icerik adini bildirir.
     */
    suspend fun describe(value: String, isInfohash: Boolean = false): String? {
        val urls = listOf(
            describeUrl(value, isInfohash),
            describeUrl(value, !isInfohash),
        ).distinct()
        for (url in urls) {
            val body = getBody(url, CHECK_TIMEOUT_MS) ?: continue
            parseMediaName(body)?.let { return it }
        }
        return null
    }

    /**
     * `get_media_files` adresini istenen parametre adini kullanarak kurar.
     *
     * Saf yardimci; parametre secimi [describe] icin test edilebilir.
     */
    internal fun describeUrl(value: String, isInfohash: Boolean): String =
        "$ENGINE_BASE$SERVICE_PATH?api_version=3&method=get_media_files&" +
            (if (isInfohash) "infohash=" else "content_id=") + value

    /**
     * `get_media_files` yanitindan kanal adini cikarir.
     *
     * Beklenen yapi:
     * ```json
     * {"result":{"files":[{"index":0,"filename":"beIN SPORTS 2"}],
     *   "name":"beIN SPORTS 2","infohash":"...","type":"live"}}
     * ```
     */
    internal fun parseMediaName(body: String): String? {
        val root = runCatching { mapper.readTree(body) }.getOrNull() ?: return null
        val result = root.get("result") ?: return null
        result.textOrNull()?.let { return it }
        // Bazi motor surumlerinde "name" yok; dosya adindan kanal adi cikarilir.
        val files = result.get("files") ?: return null
        for (file in files) {
            val filename = file.get("filename").textOrNull() ?: continue
            return filename.substringBeforeLast('.')
        }
        return null
    }

    // --- Arama yanit ayristirma ---

    /**
     * Arama yanitini ayristirir.
     *
     * Beklenen yapi:
     * ```json
     * {"result":{"total":1,"results":[
     *   {"items":[{"infohash":"...","name":"beIN SPORTS 1","status":2,"availability":1}]}]}}
     * ```
     */
    internal fun parseSearchResults(body: String): List<AceStreamResult> {
        val root = runCatching { mapper.readTree(body) }.getOrNull() ?: return emptyList()
        val groups = root.get("result")?.get("results") ?: return emptyList()
        val results = mutableListOf<AceStreamResult>()
        for (group in groups) {
            val items = group.get("items") ?: continue
            for (item in items) {
                val contentId = item.get("infohash").textOrNull()?.takeIf { isContentId(it) } ?: continue
                results += AceStreamResult(
                    name = item.get("name").textOrNull().orEmpty(),
                    contentId = contentId,
                    status = item.get("status")?.takeIf { it.isNumber }?.asInt(),
                    availability = item.get("availability")?.takeIf { it.isNumber }?.asDouble() ?: 0.0,
                )
            }
        }
        return results
    }

    // --- Ad eslesmesi ---

    /**
     * Kanal adini kucuk harf, alfanumerik kelimelere boler.
     *
     * "beIN SPORTS 1 TR" -> [bein, sports, 1, tr]
     */
    internal fun tokens(value: String): List<String> =
        value.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /**
     * Kanal adi eslesmesi.
     *
     * Hedef adin TUM kelimeleri aday adinin kelimeleri icinde mevcutsa
     * eslesme sayilir; adaydaki fazla kelimeler (HD, TR, 1080p gibi)
     * eslesmeyi bozmaz ama siralamada cezalandirilir ([extraTokens]).
     * Boylece dizindeki "beIN SPORTS HD 1 [TR]" ile "beIN Sports HD 1 TR"
     * gibi farkliliklar da yakalanir.
     *
     * Eski kurallar bozulan durumlarda hala elenir:
     * - Kanal numarasi farkliyse hedefteki numerik kelime adayda
     *   bulunmaz, dolayisiyla eslesme olmadigi icin elenir
     *   ("beIN SPORTS 1" hedefi "beIN SPORTS 2" adayini karistirmaz).
     * - Dil surekleri hedefe kelime olarak girince korunur: arama hedefi
     *   "beIN SPORTS HD 3 TR" ise aday "beIN SPORTS HD 3 FR", hedefin
     *   "tr" kelimesini kapsamadigi icin elenir.
     */
    internal fun matchesTokens(candidateName: String, target: List<String>): Boolean {
        if (target.isEmpty()) return false
        val candidate = tokens(candidateName).toSet()
        val targetSet = target.toSet()
        // Hedefe ait TUM kelimeler adayda mevcut olmali; fazlalar izinli.
        return candidate.containsAll(targetSet)
    }

    /**
     * Aday adindaki hedef disi kelime sayisi.
     *
     * Siralamada ceza puanidir: temiz eslesme (0 fazla kelime) ayni
     * durum grubunda once denenir.
     */
    internal fun extraTokens(candidateName: String, target: List<String>): Int {
        val targetSet = target.toSet()
        return tokens(candidateName).count { it !in targetSet }
    }

    // --- Altyapi ---

    /**
     * Kisa zaman asimli GET; hatada ya da zaman asiminda `null` doner.
     *
     * Onceki surumde her motor cagrisi kutuphane varsayilan (uzun) zaman
     * asimiydi: olu port ya da yanit vermeyen motor, aday sayisi kadar
     * uzun bekletme yaratıyordu. Ucler artik amacina gore kisalitildi:
     * durum sondaji 3 sn, icerik dogrulama 8 sn, arama dizini 12 sn.
     */
    private suspend fun getBody(url: String, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) { runCatching { app.get(url).text }.getOrNull() }
}

/**
 * JSON dugumundeki metni dondurur.
 *
 * Jackson'da JSON `null` degeri `NullNode` olur ve `asText()` ile "null"
 * yazisi doner. Bu yardimci, yalnizca gercek metin degerlerini kabul eder.
 */
private fun JsonNode.textOrNull(): String? =
    if (this.isTextual) asText().takeIf { it.isNotBlank() } else null

/**
 * Bir Ace Stream icerik referansi.
 *
 * Icerik iki farkli kimlikle adreslenir ve motor bunlari **birbirinin yerine
 * kullanmaz**. Cihazda olculen davranis:
 *
 * ```
 * ?content_id=<content_id>  ->  302, calisir
 * ?content_id=<infohash>    ->  HTTP 500
 * ?infohash=<infohash>      ->  302, calisir
 * ?infohash=<content_id>    ->  HTTP 500
 * ```
 *
 * Arama dizini (`/search`) **infohash** dondurur; kanal listesindeki sabit
 * degerler ise **content_id**'dir. Bu ayrim yapilmazsa arama sonucundan
 * gelen kimlikler 500 ile reddedilir.
 *
 * @property value content_id veya infohash
 * @property isInfohash Degerin infohash mi content_id mi oldugu
 */
data class AceContentRef(
    val value: String,
    val isInfohash: Boolean = false,
) {
    /**
     * Oynatma adresi.
     *
     * Motorun bekledigi parametre kullanilir; yonlendirmeyi oynatici takip
     * eder (motor ayni adrese 302 doner).
     */
    fun manifestUrl(): String =
        "${AceStream.ENGINE_BASE}${AceStream.MANIFEST_PATH}?" +
            (if (isInfohash) "infohash=" else "content_id=") + value

    /** Dogrulama (salt okunur) adresi. */
    fun checkUrl(): String =
        "${AceStream.ENGINE_BASE}${AceStream.SERVICE_PATH}?api_version=3&method=get_media_files&" +
            (if (isInfohash) "infohash=" else "content_id=") + value
}

/**
 * Ace Stream arama dizininden gelen tek bir kanal.
 *
 * @property name Kanal adi
 * @property contentId 40 haneli icerik kimligi
 * @property status 2 = yayinda, 1 = yayinda olabilir
 * @property availability 0.0 - 1.0 arasi yayin kullanilabilirligi
 */
data class AceStreamResult(
    val name: String,
    val contentId: String,
    val status: Int?,
    val availability: Double,
)
