// use an integer for version numbers
version = 25

android {
    namespace = "tr.tabii"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

cloudstream {
    language = "tr"
    // All of these properties are optional, you can safely remove them

    description = "Türkiye'nin canlı TV kanalları: tabii kanalları (TRT 1, TRT 2, TRT Spor, TRT Haber, TRT Çocuk, TRT Belgesel, TRT Müzik, TRT Türk, TRT Kürdi, TRT Arapî, TRT Avaz, TRT WORLD, TRT Genç, TRT eba), Ace Stream (P2P) kanalları (beIN SPORTS) ve TR Kanalları (TV8, ATV, Star TV, DMAX ve daha fazlası; topluluk M3U listesinden otomatik güncellenir)."
    authors = listOf("karayel90")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=tabii.com&sz=%size%"
}
