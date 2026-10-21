package tr.tabii

/**
 * Ace Stream uzerinden yayinlanan Turk kanallari.
 *
 * Bu kanallar tabii'de bulunmuyor; beIN SPORTS ve Setanta gibi ucretli
 * yayinlardir. Ace Stream P2P agi uzerinden seyredilirler.
 *
 * @property id Kanalin benzersiz anahtari
 * @property name Goruntulenecek kanal adi
 * @property contentId Ace Stream icerik kimligi (40 haneli onaltilik ozet)
 * @property logo Kanal logosunun adresi
 */
data class AceStreamChannel(
    val id: String,
    val name: String,
    val contentId: String,
    val logo: String?,
) {
    /** Kanalin `acestream://` adresi. */
    val pageUrl: String get() = AceStream.acestreamUrl(contentId)
}

/**
 * Desteklenen Ace Stream kanallari.
 *
 * Content ID'ler yayincinin dosyasini yeniden yuklemesiyle degisebiliyor.
 * Bu yuzden sabit ID'ye guvenmek yerine, oynatma aninda motorun arama
 * dizininden guncel ID tekrar bulunur (bkz. [AceStream.resolveContentId]).
 * Buradaki ID'ler oyunun hemen calismasini saglayan yedek degerlerdir.
 */
object AceStreamChannels {

    val ALL: List<AceStreamChannel> = listOf(
        AceStreamChannel(
            id = "bein-sports-1",
            name = "beIN SPORTS 1",
            contentId = "118416583faa3fb4f03800173429b6b844b43a52",
            logo = "https://i.ibb.co/3fPVPLL/Bein1.png",
        ),
        AceStreamChannel(
            id = "bein-sports-2",
            name = "beIN SPORTS 2",
            contentId = "48bee10b883f17c7186d60f1e8022dca0ba41a05",
            logo = "https://i.ibb.co/9VMNBsJ/Bein2.png",
        ),
        AceStreamChannel(
            id = "bein-sports-3",
            name = "beIN SPORTS 3",
            contentId = "ed81c333815247536edfcdb2755147e8c4144d62",
            logo = "https://i.ibb.co/GkX99sd/Bein3.png",
        ),
        AceStreamChannel(
            id = "bein-sports-4",
            name = "beIN SPORTS 4",
            contentId = "7683cd0cbacf55fa8b39c363219ffba1bc47cf9d",
            logo = "https://i.ibb.co/swxDvMT/Bein4.png",
        ),
        AceStreamChannel(
            id = "bein-sports-haber",
            name = "beIN SPORTS HABER",
            contentId = "47c1b3a68d03bd190fed521d144ad08fa0f195f3",
            logo = "https://i.ibb.co/cvTFJQt/Bein-Haber.png",
        ),
        AceStreamChannel(
            id = "setanta-sports",
            name = "Setanta Sports",
            contentId = "42fe2ce3ef6b92653a74d85b6e98c2ba0abee707",
            logo = "https://i.ibb.co/f2vxWxj/Setanta1.png",
        ),
        AceStreamChannel(
            id = "setanta-sports-plus",
            name = "Setanta Sports Plus",
            contentId = "f6d68cfa8694f814983f2a5757f4e0c4964b880c",
            logo = "https://i.ibb.co/P4sWVG3/Setanta2.png",
        ),
    )

    /** Content ID'ye gore kanal bulur. */
    fun byContentId(contentId: String): AceStreamChannel? =
        ALL.firstOrNull { it.contentId.equals(contentId, ignoreCase = true) }

    /** Kimlige gore kanal bulur. */
    fun byId(id: String): AceStreamChannel? = ALL.firstOrNull { it.id == id }

    /** Adina gore kanal bulur. */
    fun byName(name: String): AceStreamChannel? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
