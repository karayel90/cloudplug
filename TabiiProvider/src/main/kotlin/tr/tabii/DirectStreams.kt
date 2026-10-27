package tr.tabii

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Doğrudan HLS yayinlarinin (beIN SPORTS / S Sport / Tivibu Spor) makine motoru.
 *
 * Yayinlar `andro.evrenesoglu<N>.click` alan adi ailesinde barindiriliyor.
 * Operator bu alan adlarini engellendiginde numarayi degistiriyor; eklenti
 * guncellemesi beklememek icin iki katman kuruldu:
 *
 *  1. **Calisma ani cozumleme ([workingHost]):** kanal acilirken o anki
 *     calisan makine tek bir salt-okunur istekle dogrulanir; oluyse aday
 *     numaralar taranir (paralel bas + sirali kuyruk). Motor yalnizca
 *     kullanilabilir (HTTP 200 + `#EXTM3U`) makineleri listeye alir.
 *  2. **Cevarimdisi tarama (`tools/scan-streams.ps1`):** genis bir numara
 *     araligini tarar, 12 kanal yolunun tamamini dogrular ve asagidaki
 *     tarali bloktaki listeyi yeniler. Boylece yeni makineler eklenti
 *     guncellemesi beklemeden tarama sonrasi listeye girmis olur.
 *
 * Tum makineler AYNI arka ucagi paylasir (ayni segment dosyalari doner);
 * bu yuzden bulunan makine her zaman ayni yayini sunar.
 */
object DirectStreams {

    /** Doğrudan HLS kanali tanimi. */
    data class Channel(
        /** Kimlik: logo dosyasi ve kararli arama anahtari. */
        val id: String,
        /** Kullaniciya gorunen kanal adi. */
        val title: String,
        /** Makineler altinda degismeyen yayin yolu. */
        val path: String,
        /** `logos/ace/` altindaki logo kimligi. */
        val logoKey: String,
    )

    /**
     * Desteklenen 12 dogrudan spor kanali — tek dogruluk kaynagi.
     *
     * Eski `aceDirect` / `directAce` listeleri bu tipe indirgendi; kanal
     * yolu (`path`) hic degismez, yalnizca makine (host) degisir.
     */
    val CHANNELS: List<Channel> = listOf(
        Channel("bein1", "Beinsports-1-Server", "checklist/androstreamlivebs1.m3u8", "bein-sports-hd-1-tr"),
        Channel("bein2", "Beinsports-2-Server", "checklist/androstreamlivebs2.m3u8", "bein-sports-hd-2-tr"),
        Channel("bein3", "Beinsports-3-Server", "checklist/androstreamlivebs3.m3u8", "bein-sports-hd-3-tr"),
        Channel("bein4", "Beinsports-4-Server", "checklist/androstreamlivebs4.m3u8", "beinsports-4-server"),
        Channel("bein5", "Beinsports-5-Server", "checklist/androstreamlivebs5.m3u8", "beinsports-5-server"),
        Channel("ssport", "S Sport", "checklist/androstreamlivess1.m3u8", "s-sport"),
        Channel("ssport2", "S Sport 2", "checklist/androstreamlivess2.m3u8", "s-sport-2"),
        Channel("ssportplus", "S Sport Plus", "checklist/androstreamlivessplus1.m3u8", "s-sport-plus"),
        Channel("tivibu1", "Tivibu Spor 1", "checklist/androstreamlivets1.m3u8", "tivibu-spor-1"),
        Channel("tivibu2", "Tivibu Spor 2", "checklist/androstreamlivets2.m3u8", "tivibu-spor-2"),
        Channel("tivibu3", "Tivibu Spor 3", "checklist/androstreamlivets3.m3u8", "tivibu-spor-3"),
        Channel("tivibu4", "Tivibu Spor 4", "checklist/androstreamlivets4.m3u8", "tivibu-spor-4"),
    )

    // >>> scan-streams:BEGIN
    /** Son taramada dogrulanan birincil makine. */
    const val PRIMARY_HOST = "andro.evrenesoglu217.click"

    /** Son taramada 12 kanal yolu da dogrulanmis makineler (yakindan uzağa). */
    val PREFERRED_HOSTS: List<String> = listOf(
        "andro.evrenesoglu217.click",
        "andro.evrenesoglu215.click",
        "andro.evrenesoglu219.click",
        "andro.evrenesoglu221.click",
        "andro.evrenesoglu211.click",
        "andro.evrenesoglu223.click",
        "andro.evrenesoglu225.click",
        "andro.evrenesoglu227.click",
        "andro.evrenesoglu229.click",
        "andro.evrenesoglu231.click",
        "andro.evrenesoglu233.click",
        "andro.evrenesoglu235.click",
        "andro.evrenesoglu237.click",
        "andro.evrenesoglu239.click",
        "andro.evrenesoglu241.click",
        "andro.evrenesoglu243.click",
        "andro.evrenesoglu245.click",
        "andro.evrenesoglu247.click",
        "andro.evrenesoglu249.click",
        "andro.evrenesoglu251.click",
        "andro.evrenesoglu253.click",
        "andro.evrenesoglu255.click",
        "andro.evrenesoglu257.click",
        "andro.evrenesoglu259.click",
        "andro.evrenesoglu261.click",
        "andro.evrenesoglu263.click",
        "andro.evrenesoglu265.click",
        "andro.evrenesoglu267.click",
        "andro.evrenesoglu269.click",
        "andro.evrenesoglu271.click",
        "andro.evrenesoglu273.click",
        "andro.evrenesoglu275.click",
        "andro.evrenesoglu277.click",
        "andro.evrenesoglu279.click",
        "andro.evrenesoglu281.click",
        "andro.evrenesoglu283.click",
        "andro.evrenesoglu285.click",
        "andro.evrenesoglu287.click",
        "andro.evrenesoglu289.click",
        "andro.evrenesoglu291.click",
        "andro.evrenesoglu293.click",
        "andro.evrenesoglu295.click",
        "andro.evrenesoglu297.click",
        "andro.evrenesoglu299.click",
        "andro.evrenesoglu301.click",
        "andro.evrenesoglu303.click",
        "andro.evrenesoglu305.click",
        "andro.evrenesoglu307.click",
        "andro.evrenesoglu309.click",
        "andro.evrenesoglu311.click",
        "andro.evrenesoglu313.click",
        "andro.evrenesoglu315.click",
        "andro.evrenesoglu317.click",
        "andro.evrenesoglu319.click",
    )

    /** Listenin son tarama tarihi (bilgi amaclidir). */
    const val LAST_SCAN = "2026-10-27"
    // <<< scan-streams:END

    /** Makine numarasi kalibim. */
    private val HOST_NUMBER = Regex("""evrenesoglu(\d+)\.click""")

    /** Tarama sirasinda baz numaranin etrafinda taranacak yaricap. */
    internal const val SWEEP_RADIUS = 8

    /** Bir kerede eszamanli denenecek en fazla aday sayisi. */
    private const val PARALLEL_LIMIT = 3

    /** Tek bir dogrulama isteginin zaman asimi. */
    private const val PROBE_TIMEOUT_MS = 4_000L

    /**
     * Taramanin toplam butcesi: kuyruk sure dolunca durur.
     *
     * Kuyruk aday basina 4 sn'ye kadar bekleyebilir; ~60 adaylik listede bu
     * ~200+ sn (siyah-delik makinelerde) demekti — kullanici kanal acilirken
     * bekler. Butce: bas paralel (~4 sn) + kuyruk kalan sure. Butce dolunca
     * tarama erken biter ve [refreshHost]'taki gibi [PRIMARY_HOST]'a donulur
     * (kuyrunun sonuna kadar beklenmesiyle AYNI sonuc, daha hizli).
     * Not: olum makinelerin cogu hizli hata doner (NXDOMAIN/403); butce yalnizca
     * zaman asiminda bekleyenler icin sigortadir, aday sayisini kisitlemez.
     */
    private const val TOTAL_PROBE_BUDGET_MS = 12_000L

    /**
     * Son dogrulamanin ustunden kisa sure gectiyse istek atilmaz.
     *
     * Ayni etkileşim zinciri (ana sayfa -> arama -> yukleme) icin tasarlandi;
     * makine olumu ilk yeniden dokunusta fark edilsin diye kisa tutulur.
     */
    internal const val TRUST_WINDOW_MS = 5_000L

    private data class HostCache(
        val host: String,
        val checkedAt: Long,
    )

    @Volatile
    private var cache: HostCache? = null

    /** Ayni anda birden fazla taramayi engeller. */
    private val refreshMutex = Mutex()

    /**
     * Verilen metin gecerli bir HLS oynatma listesi mi?
     *
     * BOM (U+FEFF) toleransli: `Char.isWhitespace()` BOM'u bosluk saymadigi
     * icin yalnizca `trimStart()` BOM'lu playlist'i reddederdi.
     */
    internal fun isPlaylist(body: String): Boolean =
        body.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")

    /** `andro.evrenesoglu217.click` -> 217; kaliba uymuyorsa `null`. */
    internal fun hostNumber(host: String): Int? =
        HOST_NUMBER.find(host)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * `host` icindeki makine numarasini kullanarak aday listesi uretir:
     * once o adayin kendisi, sonra son taramada dogrulanmis makineler
     * (numaraya yakin olandan baslayarak), sonra numara cevresindeki
     * tarama yaricindaki tum adaylar. Yinelenenler elenir.
     */
    internal fun candidateHosts(host: String): List<String> {
        val base = hostNumber(host) ?: return listOf(host)
        val byDistance = (PREFERRED_HOSTS + PRIMARY_HOST)
            .distinct()
            .sortedBy { kotlin.math.abs((hostNumber(it) ?: 0) - base) }
        val sweep = generateSequence(base - SWEEP_RADIUS) { it + 1 }
            .take(2 * SWEEP_RADIUS + 1)
            .filter { it > 0 }
            .map { "andro.evrenesoglu$it.click" }
        return (listOf(host) + byDistance + sweep).distinct()
    }

    /** Kanalin oynatilabilir tam adresi. */
    fun urlFor(host: String, path: String): String = "https://$host/$path"

    /**
     * Adresten kanal tanimini bulur.
     *
     * Eski (farkli makineye ait) adresler de kabul edilir: eslesme yalnizca
     * kanal yoluna gore yapilir, makine adi degisebilecegi icin dikkate
     * alinmaz.
     */
    fun channelByUrl(url: String): Channel? =
        CHANNELS.firstOrNull { url.endsWith("/${it.path}") }

    /**
     * `host` makinesinin `path` yolunu sunup sunmadigini tek salt-okunur
     * istekle dogrular.
     *
     * Sertifika hatalari ve engelleme (403) dahil her tur hata "olu" sayilir;
     * cihaz da ayni sekilde reddedecektir.
     *
     * Dikkat: [kotlinx.coroutines.CancellationException] ASLA yutulmaz;
     * dis kapsam iptal edildiginde iptal yayilmaya devam eder ("olu host"
     * sanilmaz). `runCatching` bunu da yakaladigi icin kullanilmiyor.
     */
    private suspend fun hostAlive(host: String, path: String): Boolean {
        val body = try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                app.get(urlFor(host, path)).text
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        return isPlaylist(body)
    }

    /**
     * `probePath` yolunu sunan ilk calisan makineyi bulur.
     *
     * Adaylar once paralel (en fazla [PARALLEL_LIMIT]), kalanlar sirali
     * denenir; boylece yaygin durumda (ilk adaylar olu) kurtarma maliyeti
     * uc istekle sinirli kalir. Kuyruk [TOTAL_PROBE_BUDGET_MS] icinde biter;
     * butce asilirsa tarama erken sonlanir. Calisan makine yoksa `null`.
     */
    internal suspend fun probeBest(probePath: String): String? = coroutineScope {
        val hosts = candidateHosts(PRIMARY_HOST)
        // Baslangicta baslar: bas paralel (~4 sn) + kuyruk kalan sureyi tuketir
        // (AceStream.workingAndError ile ayni desen).
        val deadline = System.currentTimeMillis() + TOTAL_PROBE_BUDGET_MS
        val head = hosts.take(PARALLEL_LIMIT)
            .map { candidate -> async { candidate to hostAlive(candidate, probePath) } }
            .awaitAll()
        head.firstOrNull { it.second }?.let { return@coroutineScope it.first }
        for (candidate in hosts.drop(PARALLEL_LIMIT)) {
            if (System.currentTimeMillis() > deadline) break // butce doldu; kuyruk durur
            if (hostAlive(candidate, probePath)) return@coroutineScope candidate
        }
        null
    }

    /**
     * Kanal icin o an calisan makineyi dondurur.
     *
     * Akis:
     * 1. Cok taze (son [TRUST_WINDOW_MS]) dogrulanmis makine istek atmadan
     *    doner — ayni ekran icinde art arda cagrilar icin.
     * 2. Makine tek salt-okunur istekle KANALIN KENDI YOLU icin
     *    dogrulanir; calisiyorsa one cikarilir.
     * 3. Olu ya da dogrulanamadiysa tam tarama yapilir ([probeBest]).
     * 4. Havuzun tamami oluyse sabit [PRIMARY_HOST]'a donulur; sahte
     *    basari uretilmez, hata oynaticidan gelir.
     */
    suspend fun workingHost(channel: Channel): String {
        val now = System.currentTimeMillis()
        val snapshot = cache
        if (snapshot != null && now - snapshot.checkedAt < TRUST_WINDOW_MS) {
            return snapshot.host
        }
        return refreshHost(channel)
    }

    private suspend fun refreshHost(channel: Channel): String = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        val snapshot = cache
        if (snapshot != null && now - snapshot.checkedAt < TRUST_WINDOW_MS) {
            return@withLock snapshot.host
        }
        if (snapshot != null && hostAlive(snapshot.host, channel.path)) {
            cache = HostCache(snapshot.host, System.currentTimeMillis())
            return@withLock snapshot.host
        }
        val host = probeBest(channel.path) ?: PRIMARY_HOST
        cache = HostCache(host, System.currentTimeMillis())
        host
    }
}
