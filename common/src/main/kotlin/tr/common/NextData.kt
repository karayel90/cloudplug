package tr.common

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Next.js tabanlı sayfalar veriyi sayfa içine gömülü JSON olarak verir:
 *
 * ```
 * <script id="__NEXT_DATA__" type="application/json">{"props":{ ... }}</script>
 * ```
 *
 * Bu nesne o bloğu çıkarır ve JSON ağacına çevirir. Birçok Türkçe içerik
 * sitesi Next.js kullandığı için ortak modülde tutulur.
 */
object NextData {
    private val SCRIPT = Regex(
        """<script[^>]*\bid\s*=\s*["']__NEXT_DATA__["'][^>]*>([\s\S]*?)</script\s*>""",
        RegexOption.IGNORE_CASE,
    )

    private val mapper = ObjectMapper()

    /**
     * Sayfadaki `__NEXT_DATA__` bloğunun ham JSON metnini döndürür.
     * Bulunamazsa veya boşsa `null`.
     */
    fun extract(html: String): String? =
        SCRIPT.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * `__NEXT_DATA__` içindeki JSON ağacını döndürür.
     * Blok yoksa veya JSON bozuksa `null`.
     */
    fun tree(html: String): JsonNode? {
        val raw = extract(html) ?: return null
        return runCatching { mapper.readTree(raw) }.getOrNull()
    }

    /**
     * Verilen yolla ağaçta gezinir. Yolun herhangi bir parçası yoksa `null`.
     *
     * `node(root, "props", "pageProps", "liveChannels")`
     */
    fun node(root: JsonNode?, vararg path: String): JsonNode? {
        var current = root ?: return null
        for (part in path) {
            current = current.get(part) ?: return null
        }
        return current
    }

    /**
     * Ağacın içinde `key` alanı verilen değere eşit olan ilk nesneyi bulur.
     * `images` gibi iç içe dizilerde ilgili görseli bulmak için kullanılır.
     *
     * `findByKey(images, "key", "logo")` -> logo görselinin tanım nesnesi
     */
    fun findByKey(node: JsonNode?, key: String, value: String): JsonNode? {
        if (node == null) return null
        if (node.isObject) {
            if (node.get(key)?.asText() == value) return node
            node.fields().forEach { (_, v) -> findByKey(v, key, value)?.let { return it } }
        } else if (node.isArray) {
            node.forEach { findByKey(it, key, value)?.let { return it } }
        }
        return null
    }
}
