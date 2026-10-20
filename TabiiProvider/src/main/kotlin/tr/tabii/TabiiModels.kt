package tr.tabii

import com.fasterxml.jackson.databind.JsonNode
import tr.common.NextData

/**
 * Bir canlı kanalın tek bir medya kaydı.
 *
 * @property type "hls" ya da "dash"
 * @property drm "clear" (şifresiz), "fairplay" ya da "widevine"
 * @property url oynatılabilir manifest adresi
 * @property priority sitenin kendi tercih sırası (küçük sayı = daha öncelikli)
 */
data class TabiiMedia(
    val type: String,
    val drm: String,
    val url: String,
    val priority: Int,
)

/**
 * tabii'deki bir canlı kanal.
 */
data class TabiiChannel(
    val id: Int,
    val slug: String,
    val title: String,
    val loginStatus: String,
    val logo: String?,
    val media: List<TabiiMedia>,
) {
    /** Bu kanal tabii aboneliği gerektiriyor mu? */
    val requiresLogin: Boolean get() = loginStatus == "watched_after_login"

    /** Kanalın tabii web sayfası. */
    val pageUrl: String get() = "https://www.tabii.com/tr/watch/live/$slug?trackId=$id"

    /**
     * Oynatılabilir (şifresiz) medya kaydı.
     *
     * Tercih sırası:
     *  1. parametresiz clear HLS — doğrudan CDN, oturum/ad-server bağımlılığı yok
     *  2. herhangi bir clear HLS
     *  3. clear DASH
     *
     * DRM'li (fairplay/widevine) kayıtlar atlanır: Cloudstream plugin API'sinde
     * genel bir lisans sunucusu akışı yok.
     */
    val playableMedia: TabiiMedia?
        get() {
            val clear = media.filter { it.drm == "clear" && it.url.isNotBlank() }
            val hls = clear.filter { it.type == "hls" }
            return hls.firstOrNull { "?" !in it.url }
                ?: hls.minByOrNull { it.priority }
                ?: clear.minByOrNull { it.priority }
        }
}

/** `__NEXT_DATA__` içindeki kanal nesnesini modele çevirir. */
internal fun JsonNode.toTabiiChannel(): TabiiChannel? {
    val id = get("id")?.asInt() ?: return null
    if (id <= 0) return null
    val slug = get("slug")?.asText()?.takeIf { it.isNotBlank() } ?: return null
    val title = get("title")?.asText()?.takeIf { it.isNotBlank() } ?: return null
    val loginStatus = get("loginStatus")?.asText().orEmpty()
    val logo = NextData.findByKey(get("images"), "key", "logo")?.get("name")?.asText()
    val media = get("media")?.mapNotNull { it.toTabiiMedia() }.orEmpty()
    return TabiiChannel(id, slug, title, loginStatus, logo, media)
}

private fun JsonNode.toTabiiMedia(): TabiiMedia? {
    val url = get("url")?.asText()?.takeIf { it.isNotBlank() } ?: return null
    return TabiiMedia(
        type = get("type")?.asText().orEmpty(),
        drm = get("drmSchema")?.asText().orEmpty(),
        url = url,
        priority = get("priority")?.asInt() ?: 0,
    )
}
