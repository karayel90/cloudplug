package tr.trlive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Token servisinin çağrılacağı tam adresi üretir.
 *
 * Servis adresin başına rastgele bir sayı ekleyerek önbelleğe takılmayı
 * engelliyor. Yayın adresi `URLEncoder` ile kodlanır; bu, tarayıcıdaki
 * `encodeURIComponent` ile aynı sonucu verir.
 *
 * Bu fonksiyon bilerek CS3 sınıflarına bağlı değil: saf bir dize işlemi
 * olduğu için birim testinde ağ erişimi olmadan doğrulanabiliyor.
 */
fun buildTokenUrl(streamUrl: String, random: Int = Random.nextInt(1, 1_000_000)): String {
    val encoded = URLEncoder.encode(streamUrl, "UTF-8")
    return "${TrLiveChannels.TOKEN_HOST}?$random&url=$encoded"
}

/**
 * Daion CDN'in "secure token" (imzalı yayın) mekanizması.
 *
 * Bazı kanallarda (ATV) master playlist adresi kendiliğinden oynatılamaz;
 * adres `st` (başlangıç jetonu) ve `e` (bitiş zamanı) parametreleriyle
 * imzalanmalıdır. İmza, yayıncının kendi servisinden alınır:
 *
 * ```
 * GET https://securevideotoken.tmgrup.com.tr/webtv/secure?<rastgele>&url=<imzalanacak adres>
 * X-isApp: false
 * X-Rand: <epoch ms>
 * Origin: <yayıncı alan adı>
 * Referer: <yayıncı ana sayfası>
 * ```
 *
 * Yanıt, `st`/`e` eklenmiş nihai adresi `Url` alanında döner:
 *
 * ```json
 * {"Success":true,"Url":"https://trkvz.daioncdn.net/atv/atv.m3u8?...&st=...&e=...","Time":41600}
 * ```
 *
 * `Time` imzanın kalan geçerlilik süresini saniye cinsinden verir (yaklaşık
 * 11,5 saat), dolayısıyla jeton oynatma anında alınsa yeterlidir.
 *
 * **Origin ve Referer başlıkları zorunludur** — bu iki başlık olmadan servis
 * 403 döner.
 */
object DaionToken {

    private val mapper = ObjectMapper()

    /**
     * İmzalı yayın adresini çözer.
     *
     * @throws ErrorLoadingException Token alınamazsa.
     */
    suspend fun resolve(channel: TrLiveChannel): String {
        val body = app.get(
            buildTokenUrl(channel.streamUrl),
            headers = mapOf(
                "X-isApp" to "false",
                "X-Rand" to System.currentTimeMillis().toString(),
                "Origin" to channel.origin,
                "Referer" to channel.referer,
            ),
            referer = channel.referer,
        ).text

        val json: JsonNode? = runCatching { mapper.readTree(body) }.getOrNull()
        val url = json?.get("Url")?.asText()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException("${channel.name} için yayın jetonu alınamadı")

        return url
    }
}
