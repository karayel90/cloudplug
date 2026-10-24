package tr.tabii

import tr.common.M3u

/**
 * Topluluk M3U listesinde bulunmayan kanallar.
 *
 * Liste her türlü Türkçe kanalı toplamıyor: bazı yayıncılar (Show TV, DMAX,
 * TLC, TV2) listede hiç yok, Beyaz TV ile Teve2'nin ise yalnızca ölü kayıtları
 * var. Bu kanallar yayıncının kendi sitesindeki oynatıcıdan alınan resmi HLS
 * adresleriyle eklenir.
 *
 * ## Neden sabit?
 *
 * Bu adresler yayıncıların kendi CDN'lerinde barınıyor ve M3U listesine göre
 * daha uzun ömürlü. Adres değişirse [DeadStreams] gibi bir kara listeye
 * eklenmek yerine buradaki kayıt güncellenmelidir.
 *
 * ## Doğrulama
 *
 * 24.10.2026 tarihinde master → varyant → ilk segment zinciriyle test edildi;
 * hepsi canlı yayın verdi. Referer zorunlu: Daion CDN'i başlıksız isteği
 * reddediyor, bu yüzden her kayıtta `referer` dolu.
 *
 * NTV burada **yok**: M3U listesi zaten çalışan bir NTV kaydı taşıyor
 * (`dogus.daioncdn.net/ntv/ntv.m3u8?app=ntv_web`). Buraya ikinci kez
 * eklemek aynı yayını çift kayıt olarak kataloglamak olurdu.
 *
 * ## TV2
 *
 * `tv2.com.tr/canli-yayin` sayfasının şemasında eski bir `tv2.duhnet.tv`
 * adresi duruyor; o alan adı artık DNS'te yok. Sayfadaki gerçek oynatıcı
 * (`initLivePage`) Demirören'in Daion CDN'inde `teve2` yolunu kullanıyor, yani
 * TV2 yayını Teve2 altyapısından veriliyor. M3U'daki `demiroren-live` kaydı
 * ölü olduğu için [DeadStreams]'te; buradaki `demiroren` kaydı ise çalışıyor.
 */
internal object ExtraChannels {

    /** M3U listesine eklenen, doğrulanmış kanallar. */
    val ENTRIES: List<M3u.Entry> = listOf(
        M3u.Entry(
            title = "Show TV (1080p)",
            tvgId = "ShowTV.tr",
            logo = "https://www.showtv.com.tr/favicon.ico",
            group = "General",
            userAgent = null,
            referer = "https://www.showtv.com.tr/",
            url = "https://ciner.daioncdn.net/showtv/showtv.m3u8" +
                "?ce=3&app=4bc856ef-4c68-4a94-bc87-37dfaaa66558",
        ),
        M3u.Entry(
            title = "Beyaz TV (1080p)",
            tvgId = "BeyazTV.tr",
            logo = "https://www.beyaztv.com.tr/favicon.ico",
            group = "General",
            userAgent = null,
            referer = "https://www.beyaztv.com.tr/",
            url = "https://beyaztv-live.daioncdn.net/beyaztv/beyaztv_1080p.m3u8",
        ),
        M3u.Entry(
            title = "TV2",
            tvgId = "TV2.tr",
            logo = "https://www.tv2.com.tr/assets/img/tv2-logo.svg?v=1",
            group = "General",
            userAgent = null,
            referer = "https://www.tv2.com.tr/",
            url = "https://demiroren.daioncdn.net/teve2/teve2.m3u8" +
                "?ce=3&app=6aab838a-437e-4a1b-bbd0-e30f79cdbbbd",
        ),
        M3u.Entry(
            title = "DMAX (720p)",
            tvgId = "DMAX.tr",
            logo = "https://www.dmax.com.tr/favicon.ico",
            group = "Documentary",
            userAgent = null,
            referer = "https://www.dmax.com.tr/",
            url = "https://dogus.daioncdn.net/dmax/dmax.m3u8" +
                "?ce=3&app=5a02c599-d17e-4982-9b04-090934d51af7",
        ),
        M3u.Entry(
            title = "TLC (720p)",
            tvgId = "TLC.tr",
            logo = "https://www.google.com/s2/favicons?domain=tlc.com.tr&sz=128",
            group = "Documentary",
            userAgent = null,
            referer = "https://www.tlc.com.tr/",
            url = "https://dogus.daioncdn.net/tlc/tlc.m3u8?app=tlc_web",
        ),
    )
}
