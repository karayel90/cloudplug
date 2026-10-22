package tr.tabii

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.delay
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
    private val CONTENT_PARAMS = listOf("content_id", "id", "infohash")

    /** Icerik kimligi: 40 haneli onaltilik ozet. */
    private val CONTENT_ID = Regex("\\A[0-9a-fA-F]{40}\\z")

    /** Kanalin yayinda oldugunu bildiren arama sonucu durumu. */
    const val STATUS_AVAILABLE = 2

    private val mapper = ObjectMapper()

    /** Verilen metin gecerli bir Ace Stream content ID mi? */
    fun isContentId(value: String): Boolean = CONTENT_ID.matches(value)

    /**
     * Content ID'den HLS manifest adresi uretir.
     *
     * Motorun oynatma ucu `content_id` parametresini kabul eder (`id` eski
     * takma adidir). Yayin adresi motorun HTTP ucundan dogrudan gelir;
     * yonlendirmeyi oynatici takip eder.
     */
    fun manifestUrl(contentId: String): String = "$ENGINE_BASE$MANIFEST_PATH?content_id=$contentId"

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
     * Bir kanalin denenecek content ID'lerini dondurur.
     *
     * Once motorun arama dizininden gelen, kanal adiyla **tam eslesen** tum
     * sonuclar kullanilabilirlige gore siralanarak gelir; ardindan sabit
     * (yedek) ID'ler eklenir.
     *
     * Ace Stream'de ayni kanalin P2P aginda **birden fazla yayinci
     * (uploader) node'u** olabiliyor ve her birinin kendi content ID'si var.
     * Bunlardan biri olurken digeri oluyor. Bu yuzden tek bir ID'ye guvenmek
     * yerine hepsi denenir; aksi halde tek bir yayincinin kapanmasi kanali
     * tamamen cokertiyor.
     *
     * @param searchNames Motor dizininde aranacak adlar, oncelik sirasiyla.
     *   Her ad icin ayri arama yapilir ve sonuclar **token** eslesmesiyle
     *   filtrelenir; boylece "beIN SPORTS 1" aramasi "beIN SPORTS 1 TR"
     *   sonucunu yanlislikla almaz.
     */
    suspend fun resolveContentIds(
        searchNames: List<String>,
        pinned: List<String>,
    ): List<String> {
        val result = mutableListOf<String>()
        for (query in searchNames) {
            val target = tokens(query)
            if (target.isEmpty()) continue
            val matches = search(query)
                .filter { matchesTokens(it.name, target) }
                .sortedByDescending {
                    (if (it.status == STATUS_AVAILABLE) 10_000 else 0) +
                        (it.availability * 1000).toInt()
                }
                .map { it.contentId }
            // Onceki (oncelikli) adin sonuclari basta olsun.
            result += matches
        }
        return (result + pinned).filter { isContentId(it) }.distinct()
    }

    /**
     * Bir content ID'nin motor tarafindan tanindigini **salt okunur** bir
     * API ile dogrular.
     *
     * Dogrulama icin `/server/api?method=get_media_files` kullanilir; bu
     * uc yalnizca icerik bilgisini okur ve **oynatma oturumu acmaz**.
     *
     * Bu onemli: motorun tek bir oynaticisi var ve `format=json` ile her
     * deneme bir oturum acar. Adaylar sirayla denenirken oturumlar
     * birbirinin uzerine biner, hatta ayni anda calisan Ace Player'in
     * yayinini keser. Salt okunur dogrulama bu yuzden oturum acmaz.
     *
     * @return Basarili ise `null`, aksi halde motorun hata mesaji.
     */
    suspend fun checkContent(contentId: String): String? {
        for (param in CONTENT_PARAMS) {
            val url = "$ENGINE_BASE$SERVICE_PATH?api_version=3&method=get_media_files&$param=$contentId"
            val body = runCatching { app.get(url).text }.getOrNull() ?: continue
            val root = runCatching { mapper.readTree(body) }.getOrNull() ?: continue
            val error = root.get("error")
            if (error != null && !error.isNull) {
                return errorMessage(error) ?: "içerik yüklenemedi"
            }
            val result = root.get("result") ?: continue
            if (result.get("files")?.isEmpty == false) return null
        }
        return "içerik bulunamadı"
    }

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
     * Kanal adini kucuk harf, alfanumerik kelimelere boler.
     *
     * "beIN SPORTS 1 TR" -> [bein, sports, 1, tr]
     */
    internal fun tokens(value: String): List<String> =
        value.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /**
     * Iki kanal adi ayni kelimeleri iceriyor mu?
     *
     * "beIN SPORTS 1" ile "beIN SPORTS 1 TR" eslesmez; boylece arama sirasinda
     * farkli bir yayin (TR / HD / FR) secilmez.
     */
    internal fun matchesTokens(candidateName: String, target: List<String>): Boolean {
        if (target.isEmpty()) return false
        val candidate = tokens(candidateName)
        return candidate == target || candidate.toSet() == target.toSet()
    }
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
