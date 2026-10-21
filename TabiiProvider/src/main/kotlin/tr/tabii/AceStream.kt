package tr.tabii

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.app
import java.net.URLEncoder

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
     * digerini uygulamis olabilir.
     */
    private val STATUS_URLS = listOf(
        "$ENGINE_BASE$SERVICE_PATH?api_version=3&method=get_status",
        "$ENGINE_BASE/webui/api/service?method=get_version",
    )

    /**
     * Oynatma ucunun kabul ettigi parametre adlari, deneme sirasiyla.
     *
     * `id` `content_id` degerinin eski takma adidir; `infohash` ise arama
     * dizininin dondurdugu degerdir. Motor surumune gore hangisinin
     * tanindigi degisebiliyor.
     */
    private val PLAYBACK_PARAMS = listOf("content_id", "id", "infohash")

    /** Icerik kimligi: 40 haneli onaltilik ozet. */
    private val CONTENT_ID = Regex("\\A[0-9a-fA-F]{40}\\z")

    /** Kanalin yayinda oldugunu bildiren arama sonucu durumu. */
    const val STATUS_AVAILABLE = 2

    /**
     * Bir arama sonucunun kabul edilebilmesi icin gereken en dusuk puan.
     *
     * Bu esigin altindaki sonuclar kanal adiyla ilgisizdir; secilirse yanlis
     * kanal oynatilir.
     */
    const val MIN_MATCH_SCORE = 100

    private val mapper = ObjectMapper()

    /** Verilen metin gecerli bir Ace Stream content ID mi? */
    fun isContentId(value: String): Boolean = CONTENT_ID.matches(value)

    /** Content ID'den HLS manifest adresi uretir. */
    fun manifestUrl(contentId: String): String = "$ENGINE_BASE$MANIFEST_PATH?id=$contentId"

    /** Content ID'den JSON biçimli manifest adresi uretir. */
    fun manifestJsonUrl(contentId: String): String =
        "$ENGINE_BASE$MANIFEST_PATH?format=json&id=$contentId"

    /**
     * Manifest adresinden content ID'yi cikarir.
     *
     * `load()` cagrisindaki yayin adresinden content ID'yi geri almak icin
     * kullanilir.
     */
    fun contentIdFromManifest(url: String): String? {
        val raw = url.substringAfter("id=", "").substringBefore('&').trim()
        return raw.takeIf { isContentId(it) }
    }

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

    /**
     * Motorun calisip calismadigini kontrol eder.
     *
     * Motor cihazda kurulu degilse ya da acik degilse `false` doner; boylece
     * kullaniciya "Ace Stream motorunu baslat" gibi anlasilir bir hata
     * gosterebiliriz.
     *
     * Belgeler iki farkli durum ucu tarif ediyor; ikisi de denenir.
     */
    suspend fun isEngineRunning(): Boolean {
        for (url in STATUS_URLS) {
            val body = runCatching { app.get(url).text }.getOrNull() ?: continue
            if (body.contains("\"result\"")) return true
        }
        return false
    }

    /**
     * Motorun arama dizininde kanal arar.
     *
     * Motor calismiyorsa veya arama basarisiz olursa bos liste doner.
     */
    suspend fun search(query: String): List<AceStreamResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val url = "$ENGINE_BASE$SEARCH_PATH?query=${URLEncoder.encode(trimmed, "UTF-8")}&page_size=50"
        val body = runCatching { app.get(url).text }.getOrNull() ?: return emptyList()
        return parseSearchResults(body)
    }

    /**
     * Bir kanalin guncel content ID'sini arama dizininden bulur.
     *
     * Content ID'ler yayincinin dosyasini yeniden yuklemesiyle degisebiliyor.
     * Sabit bir ID'ye guvenmek yerine, motorun bildirdigi guncel ID tercih
     * edilir; bulunamazsa [fallback] kullanilir.
     */
    suspend fun resolveContentId(name: String, fallback: String): String {
        val best = pickBest(search(name), name) ?: return fallback
        return best.contentId
    }

    /**
     * Bir content ID icin dogrudan oynatma adresini cozer.
     *
     * Motor varsayilan olarak `format=redirect` ile 302 yonlendirmesi yapar.
     * CS3'ün `DefaultHttpDataSource` ayari farkli protokollere yonlendirmeyi
     * takip etmez (`allowCrossProtocolRedirects` varsayilan false) ve 3xx
     * yanitlari `ERROR_CODE_IO_BAD_HTTP_STATUS` hatasina yol acar. Bu yuzden
     * yonlendirme oynaticiya birakilmaz; motorun JSON API'sinden dogrudan
     * oynatma adresi alinir.
     *
     * Motor `format=json` desteklemiyorsa yanit bir m3u8 olur; bu durumda
     * [AceStreamPlayback.url] `null` kalir ve manifest adresi kullanilir.
     *
     * @return Cozumleme sonucu; motorun hata mesajini da icerir.
     */
    suspend fun resolvePlayback(contentId: String): AceStreamPlayback {
        var last = AceStreamPlayback(null, null, "")
        for (param in PLAYBACK_PARAMS) {
            val url = "$ENGINE_BASE$MANIFEST_PATH?format=json&$param=$contentId"
            val body = runCatching { app.get(url).text }.getOrNull() ?: continue
            parsePlaybackUrl(body)?.let { return AceStreamPlayback(it, null, body) }
            parseEngineError(body)?.let { return AceStreamPlayback(null, it, body) }
            // JSON degilse motor format=json'i desteklemiyor olabilir.
            last = AceStreamPlayback(null, null, body)
        }
        return last
    }

    /**
     * Motorun dondurdugu hata mesajini cikarir.
     *
     * Beklenen yapi:
     * ```json
     * {"error":{"message":"unknown content id","code":404},"response":null}
     * ```
     */
    internal fun parseEngineError(body: String): String? {
        val root = runCatching { mapper.readTree(body) }.getOrNull() ?: return null
        val error = root.get("error") ?: return null
        if (error.isNull) return null
        val message = error.get("message")?.asText()?.takeIf { it.isNotBlank() }
        val code = error.get("code")?.takeIf { it.isNumber }?.asInt()
        return when {
            message != null && code != null -> "$message (kod $code)"
            message != null -> message
            code != null -> "hata kodu $code"
            else -> "bilinmeyen hata"
        }
    }

    /**
     * `format=json` yanitindan dogrudan oynatma adresini cikarir.
     *
     * Beklenen yapi:
     * ```json
     * {"response":{"playback_url":"http://127.0.0.1:6878/ace/m/<id>/<sid>.m3u8",
     *   "infohash":"...","is_live":1},"error":null}
     * ```
     *
     * Motor hatali donerse ya da format desteklenmiyorsa `null`.
     */
    internal fun parsePlaybackUrl(body: String): String? {
        val root = runCatching { mapper.readTree(body) }.getOrNull() ?: return null
        // Motor hata donduyse oynatma adresi yoktur.
        root.get("error")?.let { if (!it.isNull) return null }
        val response = root.get("response") ?: return null
        return response.get("playback_url")?.asText()?.takeIf { it.isNotBlank() }
    }

    /**
     * Verilen content ID'nin kanal adini motorun servis API'sinden sorar.
     *
     * Listede olmayan bir ID ile girildiginde kullanilir. Bilinemezse `null`.
     */
    suspend fun describe(contentId: String): String? {
        val url = "$ENGINE_BASE$SERVICE_PATH?api_version=3&method=get_media_files&infohash=$contentId"
        val body = runCatching { app.get(url).text }.getOrNull() ?: return null
        return parseMediaName(body)
    }

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

    /**
     * Arama sonuclari arasindan istenen kanala en yakin olani secer.
     *
     * Once tam eslesme, sonra onek/icerik eslesmesi olan sonuclar tercih
     * edilir. Yayinda olan (status = 2) ve kullanilabilirligi yuksek sonuclar
     * daha yuksek puan alir.
     *
     * Hicbir sonuc yeterince benzeşmiyorsa `null` doner: yanlis bir kanali
     * oynatmaktansa eski kimligi kullanmak daha guvenlidir.
     */
    internal fun pickBest(results: List<AceStreamResult>, name: String): AceStreamResult? {
        if (results.isEmpty()) return null
        val target = normalize(name)
        if (target.isEmpty()) return null
        val best = results.maxByOrNull { score(it, target) } ?: return null
        return best.takeIf { score(it, target) >= MIN_MATCH_SCORE }
    }

    /** Bir arama sonucunun hedef kanala ne kadar uydugunu puanlar. */
    internal fun score(result: AceStreamResult, normalizedTarget: String): Int {
        val candidate = normalize(result.name)
        var points = when {
            candidate == normalizedTarget -> 1000
            candidate.startsWith(normalizedTarget) -> 400
            normalizedTarget.startsWith(candidate) -> 300
            candidate.contains(normalizedTarget) -> 200
            normalizedTarget.contains(candidate) -> 100
            else -> -100
        }
        if (result.status == STATUS_AVAILABLE) points += 50
        points += (result.availability * 10).toInt()
        return points
    }

    /** Kanal adlarini karsilastirmak icin sadelestirir. */
    internal fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "")
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

/**
 * Bir content ID icin oynatma adresi cozumleme sonucu.
 *
 * @property url Dogrudan oynatma adresi; motor JSON API'sinden alindiysa
 *   doludur, aksi halde `null`.
 * @property engineError Motorun dondurdugu hata mesaji.
 * @property response Motorun ham yaniti (tanilama icin).
 */
data class AceStreamPlayback(
    val url: String?,
    val engineError: String?,
    val response: String,
) {
    /** Cozumleme basarili mi? */
    val isResolved: Boolean get() = url != null

    /**
     * Kullaniciya gosterilecek kisa tanilama metni.
     *
     * Motorun gercek hata mesajini icerir; boylece "neden oynatilmiyor"
     * sorusu ekran bakarak cevaplanabilir.
     */
    fun diagnostic(): String {
        engineError?.let { return "Motor yanıtı: $it" }
        if (response.isNotBlank() && !response.trimStart().startsWith("{")) {
            return "Motor JSON yerine başka bir yanıt döndürdü " +
                "(format=json desteklenmiyor olabilir): ${response.take(120).trim()}"
        }
        if (response.isBlank()) return "Motor boş yanıt döndürdü."
        return "Motor yanıtı: ${response.take(160).trim()}"
    }
}
