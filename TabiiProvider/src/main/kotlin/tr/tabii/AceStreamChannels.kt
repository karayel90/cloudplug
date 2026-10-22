package tr.tabii

/**
 * Ace Stream uzerinden yayinlanan Turk kanallari.
 *
 * Bu kanallar tabii'de bulunmuyor; beIN SPORTS ve Setanta gibi ucretli
 * yayinlardir. Ace Stream P2P agi uzerinden seyredilirler.
 *
 * @property id Kanalin benzersiz anahtari
 * @property name Goruntulenecek kanal adi
 * @property searchNames Motorun arama dizininde aranacak adlar, oncelik
 *   sirasiyla. Ayni kanalin dizinde birden fazla adi olabiliyor (or. "beIN
 *   Sports 1 TR" ve "bein tr hd 1"); hepsi denenir.
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
 * Content ID'ler yayincinin dosyasini yeniden yuklemesiyle degisebiliyor.
 * Bu yuzden asil kaynak motorun arama dizinidir: oynatma aninda kanal adiyla
 * arama yapilir, motorun bildirdigi guncel ID'ler kullanilir. Buradaki
 * sabitler yalnizca arama sonucu bos dondugunde ya da motor kapaliyken
 * yedek olarak kullanilir.
 *
 * Arama adlarinda once Turkce varyantlar gelir; dizinde "beIN Sports 1",
 * "beIN Sports 1 TR", "BEIN SPORTS 1 FR" gibi ayri yayinlar birbirine
 * karismasin diye **token** eslesmesi kullanilir.
 */
object AceStreamChannels {

    val ALL: List<AceStreamChannel> = listOf(
        AceStreamChannel(
            id = "bein-sports-1",
            name = "beIN SPORTS 1",
            searchNames = listOf(
                "beIN Sports 1 TR",
                "bein tr hd 1",
                "beinsports ch1",
                "Bein Sports 1",
                "beIN Sports 1 HD",
            ),
            contentIds = listOf(
                "016d48fb89bb9505ab3f883db1bfb3a7c0a3eccc",
                "bba1905fcd1c4975aec544047bf8e4cd23ce3fe0",
                "118416583faa3fb4f03800173429b6b844b43a52",
            ),
            logo = "https://i.ibb.co/3fPVPLL/Bein1.png",
        ),
        AceStreamChannel(
            id = "bein-sports-2",
            name = "beIN SPORTS 2",
            searchNames = listOf(
                "beIN Sports 2 TR",
                "beinsports ch2",
                "Bein Sports 2",
                "beIN Sports 2 HD",
            ),
            contentIds = listOf(
                "b28db77c5084da7993395d77df96c30bb134f0a9",
                "87bff2403eb507ed8b18f9409b32afb167b516a1",
                "48bee10b883f17c7186d60f1e8022dca0ba41a05",
            ),
            logo = "https://i.ibb.co/9VMNBsJ/Bein2.png",
        ),
        AceStreamChannel(
            id = "bein-sports-3",
            name = "beIN SPORTS 3",
            searchNames = listOf(
                "beIN Sports 3 TR",
                "Bein Sports 3",
                "beIN Sports 3 HD",
            ),
            contentIds = listOf(
                "bba1905fcd1c4975aec544047bf8e4cd23ce3fe0",
                "ed81c333815247536edfcdb2755147e8c4144d62",
            ),
            logo = "https://i.ibb.co/GkX99sd/Bein3.png",
        ),
        AceStreamChannel(
            id = "bein-sports-4",
            name = "beIN SPORTS 4",
            searchNames = listOf(
                "beIN Sports 4 TR",
                "Bein Sports 4",
            ),
            contentIds = listOf(
                "b6baa046075d82da300fc1b16de96067a8b9540e",
                "7683cd0cbacf55fa8b39c363219ffba1bc47cf9d",
            ),
            logo = "https://i.ibb.co/swxDvMT/Bein4.png",
        ),
        AceStreamChannel(
            id = "bein-sports-haber",
            name = "beIN SPORTS HABER",
            searchNames = listOf(
                "beIN Sports Haber TR",
                "beIN Sports Haber",
            ),
            contentIds = listOf(
                "7683cd0cbacf55fa8b39c363219ffba1bc47cf9d",
                "47c1b3a68d03bd190fed521d144ad08fa0f195f3",
            ),
            logo = "https://i.ibb.co/cvTFJQt/Bein-Haber.png",
        ),
        AceStreamChannel(
            id = "setanta-sports",
            name = "Setanta Sports",
            searchNames = listOf(
                "Setanta Sports TR",
                "setanta sports",
                "Setanta Sports 1",
            ),
            contentIds = listOf(
                "efc60cfe5e3a349baa02bcc49f6647c21a9c3c5b",
                "c03afac2313901942a80986a083b891fb5a437fc",
                "42fe2ce3ef6b92653a74d85b6e98c2ba0abee707",
            ),
            logo = "https://i.ibb.co/f2vxWxj/Setanta1.png",
        ),
        AceStreamChannel(
            id = "setanta-sports-plus",
            name = "Setanta Sports Plus",
            searchNames = listOf(
                "Setanta Sports Plus TR",
                "setanta sports plus",
            ),
            contentIds = listOf(
                "7c02814084b8eb0e3fc5a9093fccf1d8f3999d55",
                "f6d68cfa8694f814983f2a5757f4e0c4964b880c",
            ),
            logo = "https://i.ibb.co/P4sWVG3/Setanta2.png",
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
