package tr.tabii

/**
 * Ace Stream uzerinden yayinlanan Turk kanallari.
 *
 * Bu kanallar tabii'de bulunmuyor; beIN SPORTS gibi ucretli yayinlardir.
 * Ace Stream P2P agi uzerinden seyredilirler.
 *
 * @property id Kanalin benzersiz anahtari
 * @property name Goruntulenecek kanal adi
 * @property searchNames Motorun arama dizininde aranacak adlar, oncelik
 *   sirasiyla. Ayni kanalin dizinde birden fazla adi olabiliyor; her ad
 *   icin ayri arama yapilir.
 * @property contentIds Bilinen content ID'ler, en guncelden eskiye dogru.
 *   Ayni kanalin P2P aginda birden fazla "yayinci" (uploader) node'u
 *   olabiliyor ve her birinin kendi content ID'si var. Bunlardan biri
 *   oldugunde digeri olabiliyor; bu yuzden hepsi sirayla denenir.
 * @property logo Kanal logosunun adresi
 */
data class AceStreamChannel(
    val id: String,
    val name: String,
    val searchNames: List<String>,
    val contentIds: List<String>,
    val logo: String?,
) {
    /** Birincil content ID. */
    val contentId: String get() = contentIds.first()

    /** Kanalin `acestream://` adresi. */
    val pageUrl: String get() = AceStream.acestreamUrl(contentId)
}

/**
 * Desteklenen Ace Stream kanallari.
 *
 * Kanal adlari ve content ID'ler Ace Stream istemcisindeki (Ace Player)
 * gosterimden alinmistir. Content ID'ler yayincinin dosyasini yeniden
 * yuklemesiyle degisebiliyor; bu yuzden asil kaynak motorun arama
 * dizinidir: oynatma aninda kanal adiyla arama yapilir, motorun bildirdigi
 * guncel ID'ler kullanilir. Buradaki sabitler arama sonucu bos dondugunde
 * ya da motor kapaliyken yedek olarak kullanilir.
 */
object AceStreamChannels {

    val ALL: List<AceStreamChannel> = listOf(
        AceStreamChannel(
            id = "bein-sports-hd-1-tr",
            name = "beIN SPORTS HD 1 [TR]",
            searchNames = listOf(
                "beIN SPORTS HD 1 [TR]",
                "beIN Sports HD 1 TR",
                "beIN SPORTS 1 TR",
            ),
            contentIds = listOf(
                "118416583faa3fb4f03800173429b6b844b43a52",
            ),
            logo = "https://i.ibb.co/3fPVPLL/Bein1.png",
        ),
        AceStreamChannel(
            id = "bein-sports-hd-2-tr",
            name = "beIN SPORTS HD 2 [TR]",
            searchNames = listOf(
                "beIN SPORTS HD 2 [TR]",
                "beIN Sports HD 2 TR",
                "beIN SPORTS 2 TR",
            ),
            contentIds = listOf(
                "48bee10b883f17c7186d60f1e8022dca0ba41a05",
            ),
            logo = "https://i.ibb.co/9VMNBsJ/Bein2.png",
        ),
        AceStreamChannel(
            id = "bein-sports-hd-3-tr",
            name = "beIN SPORTS HD 3 [TR]",
            searchNames = listOf(
                "beIN SPORTS HD 3 [TR]",
                "beIN Sports HD 3 TR",
                "beIN SPORTS 3 TR",
            ),
            contentIds = listOf(
                "ed81c333815247536edfcdb2755147e8c4144d62",
            ),
            logo = "https://i.ibb.co/GkX99sd/Bein3.png",
        ),
    )

    /** Content ID'ye gore kanal bulur. */
    fun byContentId(contentId: String): AceStreamChannel? =
        ALL.firstOrNull { ch -> ch.contentIds.any { it.equals(contentId, ignoreCase = true) } }

    /** Kimlige gore kanal bulur. */
    fun byId(id: String): AceStreamChannel? = ALL.firstOrNull { it.id == id }

    /** Adina gore kanal bulur. */
    fun byName(name: String): AceStreamChannel? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
