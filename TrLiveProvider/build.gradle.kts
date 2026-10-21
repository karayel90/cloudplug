// use an integer for version numbers
version = 1

android {
    namespace = "tr.trlive"
}

dependencies {
    testImplementation(libs.junit)
}

cloudstream {
    language = "tr"
    description = "Türkiye'nin büyük yayıncılarından canlı TV kanalları: TV8, ATV, Star TV (STARTV) ve DMAX."

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

    iconUrl = "https://www.google.com/s2/favicons?domain=startv.com.tr&sz=%size%"
}
