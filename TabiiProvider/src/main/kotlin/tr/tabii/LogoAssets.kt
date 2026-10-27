package tr.tabii

/**
 * Kendi barındırdığımız, kart oranına sığdırılmış kanal logoları.
 *
 * Cloudstream kartları görseli centerCrop ile çiziyor. Kanal logolarının
 * oranları çok farklı olduğu için ham logo ya yanlardan ya üstten/alttan
 * kesiliyordu. [tools/build-logos.ps1] her logoyu şeffaf bir kare tuvale,
 * ortadaki güvenli alana yerleştirir; kare tuval hem yatay hem dikey kartta
 * kırpılmadan göründüğü için logo her iki düzende de tam çıkar.
 *
 * Üç kaynak ayrı klasörlerde tutulur:
 *  - logos/         : TR Kanalları (M3U) — kanal adının düzleştirilmiş hâli
 *  - logos/tabii/   : tabii kanalları — kanalın slug değeri
 *  - logos/sports/  : doğrudan HLS spor kanalları — kanal kimliği
 *
 * Listede olmayan (ör. M3U'ya sonradan eklenen) kanallar ham logo adresine
 * düşer; yeni kanal için logo üretmek üzere betik yeniden çalıştırılır.
 */
internal object LogoAssets {

    private const val BASE =
        "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/"

    /** logos/ altında dosyası bulunan TR kanallarının düzleştirilmiş adları. */
    private val KEYS: Set<String> = setOf(
        "4utv",
        "360tv",
        "a2tv",
        "ahaber",
        "aspor",
        "aksutv",
        "alzahratvturkic",
        "alanyapostatv",
        "almahriahtv",
        "anadolunettv",
        "arastv",
        "atv",
        "atvalanya",
        "benguturktv",
        "bloomberght",
        "brtv",
        "bursaastv",
        "cnbce",
        "ciftcitv",
        "denizpostasitv",
        "dha",
        "disneyjr",
        "diyanettv",
        "diyartv",
        "dosttv",
        "dreamturk",
        "edessatv",
        "elsharqtv",
        "ertv",
        "erzurumwebtv",
        "estv",
        "etvkayseri",
        "eurod",
        "finansturktv",
        "flashhabertv",
        "fortunatv",
        "fx",
        "haber61tv",
        "haberglobal",
        "haberturktv",
        "halktv",
        "htsportv",
        "hunattv",
        "ilketv",
        "kanal3",
        "kanal7",
        "kanal7avrupa",
        "kanal12",
        "kanal15",
        "kanal23",
        "kanal26",
        "kanal58",
        "kanald",
        "kanalddrama",
        "kanalfirat",
        "kanalv",
        "kaytv",
        "kentturktv",
        "konyaolaytv",
        "lalegultv",
        "lifetv",
        "luystv",
        "mavikaradeniz",
        "medmuzik",
        "mekameleentv",
        "meltemtv",
        "mercantv",
        "minikacocuk",
        "minikago",
        "nowtv",
        "ntv",
        "number1ask",
        "number1damar",
        "number1dance",
        "number1tv",
        "powerdance",
        "powerlove",
        "powerturk",
        "powerturkakustik",
        "powerturkslow",
        "powerturktaptaze",
        "powertv",
        "powerturktv",
        "satranctv",
        "semerkandtv",
        "sercemtv",
        "startv",
        "sunrtv",
        "tbmmtv",
        "tele1",
        "tempotv",
        "tgrthaber",
        "tivi6",
        "tjktv2",
        "tjktv",
        "tontv",
        "tr24tv",
        "trt1",
        "trtarabi",
        "trtavaz",
        "trtbelgesel",
        "trtcocuk",
        "trtdiyanetcocuk",
        "trtebaortaokul",
        "trthaber",
        "trtkurd",
        "trtmuzik",
        "trtspor",
        "trtsporyildiz",
        "trtturk",
        "trtworld",
        "trtworldhd",
        "tv4",
        "tv8",
        "tv24",
        "tv41",
        "tv264",
        "tvden",
        "urfanatiktv",
        "van65tv",
        "vavtv",
        "zaroktv",
        "showtv",
        "beyaztv",
        "tv2",
        "dmax",
        "tlc",
    )

    /** logos/tabii/ altında dosyası bulunan tabii kanallarının slug değerleri. */
    private val TABII_KEYS: Set<String> = setOf(
        "tabiispor",
        "tabiitv",
        "trt1",
        "tabii-cocuk",
        "trt2",
        "trtspor",
        "trtsporyildiz",
        "trthaber",
        "trtcocuk",
        "trtworld",
        "trtbelgesel",
        "trtmuzik",
        "trtturk",
        "trtkurdi",
        "trtarabi",
        "trtavaz",
        "trtdiyanetcocuk",
        "trteba",
        "trtgenc",
        "tabiispor1",
        "tabiispor2",
        "tabiispor3",
        "tabiispor4",
        "tabiispor5",
        "tabiispor6",
        "tabiispor7",
        "tabiispor8",
    )

    /** logos/ace/ altında dosyası bulunan Ace Stream kanallarının kimlikleri. */
    private val ACE_KEYS: Set<String> = setOf(
        "bein-sports-hd-1-tr",
        "bein-sports-hd-2-tr",
        "bein-sports-hd-3-tr",
        "beinsports-4-server",
        "beinsports-5-server",
        "s-sport",
        "s-sport-2",
        "s-sport-plus",
        "tivibu-spor-1",
        "tivibu-spor-2",
        "tivibu-spor-3",
        "tivibu-spor-4",
    )

    /** logos/sports/ altında dosyası bulunan doğrudan HLS spor kanallarının kimlikleri. */
    private val SPORTS_KEYS: Set<String> = setOf(
        "bein-sports-1-tr",
        "bein-sports-2-tr",
        "bein-sports-3-tr",
        "bein-sports-4",
        "bein-sports-5",
        "s-sport",
        "s-sport-2",
        "s-sport-plus",
        "tivibu-spor-1",
        "tivibu-spor-2",
        "tivibu-spor-3",
        "tivibu-spor-4",
    )

    /**
     * TR kanalının poster adresini döndürür: hazır logo varsa kendi
     * depomuzdan, yoksa M3U'nun verdiği ham adres.
     */
    fun posterFor(key: String, fallback: String?): String? =
        if (key in KEYS) BASE + key + ".png" else fallback

    /** tabii kanalı için poster adresi; hazır logo yoksa ham adres. */
    fun tabiiPosterFor(slug: String, fallback: String?): String? =
        if (slug in TABII_KEYS) BASE + "tabii/" + slug + ".png" else fallback

    /** Ace Stream kanalı için poster adresi; hazır logo yoksa ham adres. */
    fun acePosterFor(id: String, fallback: String?): String? {
        val localPath = System.getProperty("user.dir")
        val file = java.io.File(localPath, "logos/ace/$id.png")
        if (!file.exists()) {
            val alt = java.io.File(localPath.replace("\\", "/"), "TabiiProvider/logos/ace/$id.png")
            if (alt.exists()) return alt.toURI().toString()
        } else {
            return file.toURI().toString()
        }
        return if (id in ACE_KEYS) BASE + "ace/" + id + ".png" else fallback
    }

    /** Doğrudan HLS spor kanalı için poster adresi; önbellek varsa yerel, yoksa uzaktan. */
    fun sportsPosterFor(id: String, fallback: String?): String? {
        val localPath = System.getProperty("user.dir")
        val file = java.io.File(localPath, "logos/sports/$id.png")
        if (!file.exists()) {
            val alt = java.io.File(localPath.replace("\\", "/"), "TabiiProvider/logos/sports/$id.png")
            if (alt.exists()) return alt.toURI().toString()
        } else {
            return file.toURI().toString()
        }
        return if (id in SPORTS_KEYS) BASE + "sports/" + id + ".png" else fallback
    }
}
