package tr.common

/**
 * M3U (#EXTM3U) çalma listesi ayrıştırıcısı.
 *
 * ## Neden gerekli?
 *
 * Türkçe canlı kanalların toplu listesi community kaynaklarında M3U biçiminde
 * tutuluyor. Bu biçim, kanal adının yanında logo, kategori, çözünürlük ve
 * bazı kanallar için zorunlu `User-Agent` / `Referer` başlıklarını da
 * taşıyabilir. Yayın adresleri zamanla değiştiği için liste **statik katalog
 * olarak değil, çalışma anında çekilip ayrıştırılarak** kullanılır.
 *
 * ## Desteklenen yönergeler
 *
 * ```m3u
 * #EXTM3U
 * #EXTINF:-1 tvg-id="ATV.tr@SD" tvg-logo="https://.../atv.png" user-agent="UA dizisi",ATV (1080p)
 * #EXTVLCOPT:http-user-agent=UA dizisi
 * #EXTVLCOPT:http-referrer=https://www.vtv.net.tr/
 * https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_1080p.m3u8
 * ```
 *
 * - `tvg-id` — kanalın benzersiz kimliği (EPG eşleştirmesi için)
 * - `tvg-logo` — kanal logosu
 * - `group-title` — kategori (News, Sports, Kids...)
 * - `user-agent` / `#EXTVLCOPT:http-user-agent` — yayın adresinin beklediği
 *   kullanıcı aracısı; bazı CDN'ler bu olmadan isteği reddediyor
 * - `referrer` / `#EXTVLCOPT:http-referrer` — yayın adresinin beklediği
 *   yönlendirici
 *
 * `#EXTVLCOPT` satırları **izleyen** kayda uygulanır (VLC davranışı); `EXTINF`
 * satırındaki aynı adlı öznitelik önceliklidir. Başlıkta çözünürlük etiketi
 * (örn. `1080p`, `4K`) ve durum uyarıları (`[Not 24/7]`, `[Geo-blocked]`)
 * bulunabilir; çözünürlük puanlamada kullanılır, gösterimde korunur.
 */
object M3u {

    /**
     * Tek bir kanal kaydı.
     *
     * @property title Kanal başlığı; çözünürlük ve uyarı etiketlerini içerir
     *   (örn. "ATV (1080p)").
     * @property tvgId Kanal kimliği; olmayan kayıtlarda `null`.
     * @property logo Kanal logosu; bilinmiyorsa `null`.
     * @property group Kategori (grup başlığı); bilinmiyorsa `null`.
     * @property userAgent Yayının beklediği kullanıcı aracısı; yoksa `null`.
     * @property referer Yayının beklediği yönlendirici; yoksa `null`.
     * @property url Yayın manifest adresi (HLS/DASH).
     */
    data class Entry(
        val title: String,
        val tvgId: String?,
        val logo: String?,
        val group: String?,
        val userAgent: String?,
        val referer: String?,
        val url: String,
    ) {
        /** Çözünürlük yüksekliği (piksel); başlıkta belirtilmemişse 0. */
        val resolution: Int = M3u.resolutionOf(title)

        /**
         * [M3u.normalizeName] ile düzleştirilmiş ad.
         *
         * Katalog kurulurken aynı ad üç kez normalize ediliyordu (çift kanal
         * temizliği, popülerlik sıralaması, kart anahtarı). Sonuç kayda bağlı
         * olduğu için bir kez hesaplanıp saklanır; `copy()` yeni kayıtta
         * yeniden hesaplar.
         */
        val normalizedTitle: String = M3u.normalizeName(title)
    }

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val RES = Regex("""(\d{3,4})p""")
    private val PAREN_TAG = Regex("""\([^)]*\)""")
    private val BRACKET_TAG = Regex("""\[[^\]]*]""")
    private val NON_ALNUM = Regex("[^a-z0-9]")

    /**
     * M3U metnini kayıt listesine çevirir.
     *
     * Başlığı belirlerken `",` dizisinin **son** geçtiği yere bakılır:
     * öznitelik değerleri (özellikle `User-Agent` dizileri) virgül içerebilir,
     * dolayısıyla "ilk virgülden sonrası başlıktır" kuralı burada kırılır.
     * Öznitelik hiç yoksa ilk virgülden sonrası başlık sayılır.
     *
     * Adresi olmayan (bozuk) kayıtlar sessizce atlanır.
     */
    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var pending: PendingEntry? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    pending?.let { if (it.url != null) entries += it.build() }
                    pending = parseExtInf(line)
                }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> {
                    val value = line.substringAfter('=')
                    pending?.let { it.userAgent = value }
                }
                line.startsWith("#EXTVLCOPT:http-referrer=") -> {
                    val value = line.substringAfter('=')
                    pending?.let { it.referer = value }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val target = pending
                    if (target != null) {
                        target.url = line
                        entries += target.build()
                        pending = null
                    }
                }
            }
        }
        pending?.let { if (it.url != null) entries += it.build() }
        return entries
    }

    /**
     * Çift kanalları temizler: aynı kanalın birden fazla çözünürlükteki
     * kayıtlarından **en yüksek çözünürlüklü** olanı kalır.
     *
     * Aynı kanalın M3U listesinde birden fazla kaydı olabilir (aynı yayının
     * farklı CDN ya da çözünürlük versiyonları). Kanal kimliğini düzleştirilmiş
     * ad belirler — `tvg-id` güvenilir değildir; bazı liste kaynakları farklı
     * kanallara aynı kimliği yazar. Ad düzleştirmesi Türkçe karakterleri
     * kapsar; böylece "BENGÜTÜRK TV" ile "Benguturk TV" tek kanal sayılır.
     *
     * Eşitlikte önce https tercih edilir (bazı Android sürümleri cleartext
     * HTTP'yi kısıtlar), sonra ilk bulunan korunur.
     */
    fun dedupeByBestResolution(entries: List<Entry>): List<Entry> {
        val best = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            val key = entry.normalizedTitle
            if (key.isEmpty()) continue
            val current = best[key]
            if (current == null || isBetter(entry, current)) best[key] = entry
        }
        return best.values.toList()
    }

    /**
     * Kanal adını çift kanal eşleştirmesi için düzleştirir.
     *
     * Çözünürlük/uyarı etiketleri atılır, Türkçe karakterler sadeleştirilir
     * ve yalnızca harf-rakam kalır. "ATV (360p)" ile "ATV" böylece aynı
     * kanala işaret eder.
     */
    fun normalizeName(raw: String): String {
        if (raw.isEmpty()) return ""
        // Regex'ler önceden derlenir: normalizeName katalog kurulumunda her
        // kayıt için birkaç kez çağrılıyor. Ayrıca harfler döngüde küçük
        // harfe çevrildiği için sonda ayrı bir `lowercase()` gerekmez.
        val stripped = raw
            .replace(PAREN_TAG, " ")
            .replace(BRACKET_TAG, " ")
        val ascii = buildString(stripped.length) {
            for (c in stripped) {
                val lower = c.lowercaseChar()
                append(
                    when (lower) {
                        'ı', 'i' -> 'i'
                        'ş' -> 's'
                        'ğ' -> 'g'
                        'ü' -> 'u'
                        'ö' -> 'o'
                        'ç' -> 'c'
                        else -> lower
                    }
                )
            }
        }
        return ascii.replace(NON_ALNUM, "")
    }

    /**
     * Başlıktan çözünürlük yüksekliğini çıkarır.
     *
     * `4K` ve `8K` etiketleri piksel karşılıklarına çevrilir; çözünürlük
     * belirtilmemişse 0 döner.
     */
    fun resolutionOf(title: String): Int {
        val lower = title.lowercase()
        if ("8k" in lower) return 4320
        if ("4k" in lower || "2160p" in lower) return 2160
        return RES.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** Yeni aday, mevcut kayıttan daha iyi mi? */
    private fun isBetter(candidate: Entry, current: Entry): Boolean {
        if (candidate.resolution != current.resolution) {
            return candidate.resolution > current.resolution
        }
        val candidateHttps = candidate.url.startsWith("https://")
        val currentHttps = current.url.startsWith("https://")
        if (candidateHttps != currentHttps) return candidateHttps
        return false
    }

    /** `#EXTINF` satırını ayrıştırır. */
    private fun parseExtInf(line: String): PendingEntry {
        val body = line.removePrefix("#EXTINF:")
        val attributes = ATTR.findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
        val title = titleOf(body)
        return PendingEntry(
            title = title,
            tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
            logo = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
            group = attributes["group-title"]?.takeIf { it.isNotBlank() },
            userAgent = attributes["user-agent"]?.takeIf { it.isNotBlank() }
                ?: attributes["http-user-agent"]?.takeIf { it.isNotBlank() },
            referer = attributes["referrer"]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Başlığı bulur: öznitelikler varsa son `",` dizisinden sonrası,
     * yoksa ilk virgülden sonrası.
     */
    private fun titleOf(body: String): String {
        val lastAttrEnd = body.lastIndexOf("\",")
        return if (lastAttrEnd >= 0) {
            body.substring(lastAttrEnd + 2).trim()
        } else {
            body.substringAfter(',').trim()
        }
    }

    /** Ayrıştırma sırasında tamamlanmamış kayıt. */
    private class PendingEntry(
        val title: String,
        val tvgId: String?,
        val logo: String?,
        val group: String?,
        var userAgent: String?,
        var referer: String?,
        var url: String? = null,
    ) {
        fun build(): Entry = Entry(
            title = title,
            tvgId = tvgId,
            logo = logo,
            group = group,
            userAgent = userAgent,
            referer = referer,
            url = url!!,
        )
    }
}
