package tr.tabii

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Ace Stream uygulamasina `acestream://` adresiyle yonlendirme.
 *
 * ## Neden?
 *
 * Ace Stream kanallari P2P (BitTorrent) agi uzerinden dagitilir. Yayini
 * agdan **cek olan** sey Ace Stream uygulamasinin motorudur; CS3'ün ExoPlayer'i
 * sadece HTTP segmentlerini okur.
 *
 * Motorun **tek bir oynaticisi** vardir. CS3'te oynatildiginda iki ayri istek
 * atilir:
 *
 * ```
 * CS3ExoPlayer: loadOnlinePlayer  .../ace/manifest.m3u8?content_id=...   (oynatma)
 * PreviewImgM3u8: Loading with url = .../ace/manifest.m3u8?content_id=...  (onizleme)
 * ```
 *
 * Ikisi de ayni anda motorun HTTP ucuna gider ve her biri bir oynatma
 * oturumu acar. Motor iki oturuma birden acik kalamayinca
 * `HTTP 500 (failed to load content)` doner.
 *
 * Yonelendirme yapildiginda CS3 motorun HTTP ucuna hic dokunmaz; yayini
 * tamamen Ace Stream uygulamasi yonetir. Boylece:
 *
 * - onizleme/oynatma cakismasi olusmaz,
 * - motorun TEK oynaticisi baska biriyle cakismaz,
 * - CS3'ün onbelleklenmis hata durumu tekrar tekrar gosterilmez.
 *
 * ## Uygulama
 *
 * `acestream://` semasi Android'de Ace Stream tarafindan islenir
 * (`org.acestream.engine.ContentStartActivity`, `exported=true`).
 */
object AceStreamApp {

    private const val TAG = "TabiiProvider"

    /**
     * Ace Stream motorunu tasiyan bilinen paket adlari.
     *
     * Ace Stream Engine (org.acestream.engine) ve eski Ace Stream Media
     * surumleri (org.acestream.media) farkli paketlerle dagitiliyor.
     */
    private val ENGINE_PACKAGES = listOf("org.acestream.engine", "org.acestream.media")

    /**
     * CS3 uygulamasinin Context'ini bulur.
     *
     * CS3, context'i `com.lagradost.api.getContext()` uzerinden resmi API
     * olarak sunar; deger `CloudStreamApp.attachBaseContext` sirasinda
     * `setContext` ile kaydedilir, yani plugin kodundan hep once hazirdir.
     * Yansima gerekmez; cagri sabit ismdir (~mikrosaniye).
     *
     * Duzeltme notu (cihaz testinde yakalandi): onceki kod
     * `CloudStreamApp` sinifinda STATIK `getContext` ariyordu; oysa metot
     * `@JvmStatic` olmadan companion objesinde oldugundan CS3 4.8.0'da
     * `NoSuchMethodException` ile hep basarisiz kaliyordu (log:
     * "CS3 bağlamı bulunamadı: ...CloudStreamApp.getContext"). Sonuc:
     * motor asla otomatik baslatilamiyor ve uygulamaya devir atlaniyordu.
     *
     * @return Android'de CS3 baglami; kayit yoksa `null`.
     */
    fun cs3Context(): Context? = try {
        com.lagradost.api.getContext() as? Context
    } catch (t: Throwable) {
        Log.w(TAG, "CS3 bağlamı alınamadı: ${t.message}")
        null
    }

    // --- canHandle onbellegi (yardimci tipler) ---

    private data class CanHandleCache(val result: Boolean, val checkedAt: Long)

    private val canHandleCache = java.util.concurrent.ConcurrentHashMap<String, CanHandleCache>()

    private const val CAN_HANDLE_TTL_MS = 30_000L

    /**
     * `acestream://` adresini isleyen bir uygulama var mi?
     *
     * `queryIntentActivities` sistem cagrisidir (5-20 ms); ayni adres icin
     * sonuc kisa sure onbelleklendirilir (kanal yukleme basina 2 cagri).
     * Kullanici Ace Stream'i 30 sn icinde kurarsa gec fark edilir — bu son
     * derece nadirdir (uygulama bir kez kurulur).
     *
     * @param uri `acestream://<content-id>` adresi
     */
    fun canHandle(context: Context, uri: String): Boolean {
        val now = System.currentTimeMillis()
        canHandleCache[uri]?.let {
            if (now - it.checkedAt < CAN_HANDLE_TTL_MS) return it.result
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            val pm = context.packageManager
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            // queryIntentActivities platform tipi dondurur; null/ bos ikisi de
            // "isleyen uygulama yok" anlamina gelir.
            val result = !list.isNullOrEmpty()
            canHandleCache[uri] = CanHandleCache(result, now)
            result
        } catch (t: Throwable) {
            Log.w(TAG, "Şema kontrolü başarısız: ${t.message}")
            false
        }
    }

    /**
     * Ace Stream motorunun uygulamasini baslatir.
     *
     * Motor kapaliyken kanal acilursa kullaniciyi "ac ve geri don" adimindan
     * kurtarmak icin provider motoru otomatik baslatir; motorun HTTP ucu
     * kalkana dek provider [AceStream.awaitEngine] ile bekler.
     *
     * Yol sirasi: bilinen motor paketlerinin kendi baslatma niyetleri
     * (nötr: uygulama acilir, oturum acilmaz) once denenir; baslatma
     * niyeti bulunamazsa cagiran taraf `acestream://` semasiyla devir yapar.
     */
    fun launchEngine(context: Context): Boolean {
        for (pkg in ENGINE_PACKAGES) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Ace Stream motoru başlatıldı: $pkg")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "Motor başlatılamadı ($pkg): ${t.message}")
            }
        }
        return false
    }

    /**
     * `acestream://` adresini ilgili uygulamaya gonderir.
     *
     * @return Adres gonderildiyse `true`, gonderilemiyorsa `false`.
     */
    fun launch(context: Context, contentId: String): Boolean {
        val uri = AceStream.acestreamUrl(contentId)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            // Icerik kimligi (40 haneli hash) loglara maskeylenir; tasma
            // hatalarinda tam adres gerekmez, hizip tespit yeterli.
            Log.d(TAG, "Ace Stream uygulamasına yönlendirildi: acestream://${contentId.take(8)}…")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Yönlendirme başarısız: ${t.message}")
            false
        }
    }
}
