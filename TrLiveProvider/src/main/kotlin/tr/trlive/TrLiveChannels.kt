package tr.trlive

/**
 * Bir yayıncının canlı yayın tanımı.
 *
 * Türkiye'nin büyük yayıncıları (TV8, ATV, Star TV, DMAX) yayınlarını
 * Daion CDN üzerinden HLS olarak veriyor. Adres şablonu her kanal için sabit:
 *
 * ```
 * https://<cdn>/<kanal>/<kanal>.m3u8?app=<app-uuid>&ce=3
 * ```
 *
 * `app` değeri kanal başına sabittir ve master playlist her istekte kendini
 * yenileyen `sid` token'ları üretir. ATV dışındaki kanallarda bu adres
 * doğrudan oynatılabilir.
 *
 * @property id Cloudstream içinde kanalı tanımlayan benzersiz anahtar.
 * @property name Kanal adı (arama sonuçlarında görünür).
 * @property pageUrl Kanalın insan tarafından okunabilir canlı yayın sayfası.
 * @property manifestUrl Token'siz manifest adresi (yol + parametreler).
 * @property streamUrl İlk istekte kullanılacak tam adres.
 * @property referer CDN'in beklediği Referer başlığı.
 * @property origin CDN'in beklediği Origin başlığı.
 * @property logo Kanal logosu.
 * @property requiresToken Yayının geçici imzalı (secure token) olması gerekiyor mu?
 */
data class TrLiveChannel(
    val id: String,
    val name: String,
    val pageUrl: String,
    val manifestUrl: String,
    val streamUrl: String,
    val referer: String,
    val origin: String,
    val logo: String?,
    val requiresToken: Boolean = false,
) {
    /** Yayın adresi bu kanalın manifest adresiyle mi başlıyor? */
    fun ownsStream(data: String): Boolean = data.startsWith(manifestUrl)
}

/**
 * Desteklenen canlı yayın kanalları.
 *
 * Tüm kanallar aynı Daion altyapısını kullanıyor; ATV tek istisna, çünkü
 * yayın adresi `securevideotoken.tmgrup.com.tr` üzerinden imzalanmadan
 * oynatılamıyor.
 */
object TrLiveChannels {

    /** ATV'nin masaüstü oynatıcısının kullandığı Daion uygulama kimliği. */
    const val ATV_APP_ID = "d1ce2d40-5256-4550-b02e-e73c185a314e"

    /** ATV'nin mobil oynatıcısının kullandığı Daion uygulama kimliği (yedek). */
    const val ATV_APP_ID_MOBILE = "d5eb593f-39d9-4b01-9cfd-4748e8332cf0"

    /** securevideotoken servisinin taban adresi. */
    const val TOKEN_HOST = "https://securevideotoken.tmgrup.com.tr/webtv/secure"

    val ALL: List<TrLiveChannel> = listOf(
        TrLiveChannel(
            id = "tv8",
            name = "TV8",
            pageUrl = "https://www.tv8.com.tr/canli-yayin",
            manifestUrl = "https://tv8.daioncdn.net/tv8/tv8.m3u8",
            streamUrl = "https://tv8.daioncdn.net/tv8/tv8.m3u8?app=7ddc255a-ef47-4e81-ab14-c0e5f2949788&ce=3",
            referer = "https://www.tv8.com.tr/",
            origin = "https://www.tv8.com.tr",
            logo = "https://img.tv8.com.tr/s/template/v2/img/tv8-yeni.jpg",
        ),
        TrLiveChannel(
            id = "atv",
            name = "ATV",
            pageUrl = "https://www.atv.com.tr/canli-yayin",
            manifestUrl = "https://trkvz.daioncdn.net/atv/atv.m3u8",
            streamUrl = "https://trkvz.daioncdn.net/atv/atv.m3u8?ce=3&app=$ATV_APP_ID",
            referer = "https://www.atv.com.tr/",
            origin = "https://www.atv.com.tr",
            logo = "https://iatv.tmgrup.com.tr/site/v2/i/apple-touch-icon.png",
            requiresToken = true,
        ),
        TrLiveChannel(
            id = "startv",
            name = "Star TV",
            pageUrl = "https://www.startv.com.tr/canli-yayin",
            manifestUrl = "https://dogus.daioncdn.net/startv/startv.m3u8",
            streamUrl = "https://dogus.daioncdn.net/startv/startv.m3u8?ce=3&app=a20ac41e-bdc3-4aa1-934d-26b484480ac9",
            referer = "https://www.startv.com.tr/",
            origin = "https://www.startv.com.tr",
            logo = "https://media.startv.com.tr/star-tv/images/logopic.jpg?height=160",
        ),
        TrLiveChannel(
            id = "dmax",
            name = "DMAX",
            pageUrl = "https://www.dmax.com.tr/canli-izle",
            manifestUrl = "https://dogus.daioncdn.net/dmax/dmax.m3u8",
            streamUrl = "https://dogus.daioncdn.net/dmax/dmax.m3u8?ce=3&app=5a02c599-d17e-4982-9b04-090934d51af7",
            referer = "https://www.dmax.com.tr/",
            origin = "https://www.dmax.com.tr",
            logo = "https://www.dmax.com.tr/favicon.ico",
        ),
    )

    /** Kimliğe göre kanal bulur. */
    fun byId(id: String): TrLiveChannel? = ALL.firstOrNull { it.id == id }

    /**
     * Oynatma adresinden kanalı bulur.
     *
     * `loadLinks` eline sadece yayın adresini aldığı için, adresin hangi
     * kanalın manifest'iyle başladığına bakarak kanalı geri çözüyoruz.
     */
    fun byStreamUrl(data: String): TrLiveChannel? = ALL.firstOrNull { it.ownsStream(data) }
}
