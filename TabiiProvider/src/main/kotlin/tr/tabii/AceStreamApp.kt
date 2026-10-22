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
     * CS3 uygulamasinin Context'ini bulur.
     *
     * CS3'ün uygulama modulu siniflari runtime'da pluginin classloader'ina
     * erisilebilirdir, ancak derleme zamaninda gorunmez (plugin yalnizca
     * `cloudstream3` kutuphanesine karsi derlenir). Bu yuzden yansima
     * kullanilir.
     */
    fun cs3Context(): Context? = try {
        val cls = Class.forName("com.lagradost.cloudstream3.CloudStreamApp")
        val getter = cls.getMethod("getContext")
        getter.invoke(null) as? Context
    } catch (t: Throwable) {
        Log.w(TAG, "CS3 bağlamı bulunamadı: ${t.message}")
        null
    }

    /**
     * Verilen adresi isleyen bir uygulama var mi?
     *
     * @param uri `acestream://<content-id>` adresi
     */
    fun canHandle(context: Context, uri: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        val pm = context.packageManager
        val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        list != null && list.isNotEmpty()
    } catch (t: Throwable) {
        Log.w(TAG, "Şema kontrolü başarısız: ${t.message}")
        false
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
            Log.d(TAG, "Ace Stream uygulamasına yönlendirildi: $uri")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Yönlendirme başarısız: ${t.message}")
            false
        }
    }
}
