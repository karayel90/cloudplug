# Türkçe Cloudstream Eklentileri'ne Katkı

Katkılarınız için teşekkürler! Aşağıdaki adımları izleyerek yeni bir provider
ekleyebilir ya da mevcut olanları düzeltebilirsiniz.

## Yeni bir provider ekleme

### 1. Klasör yapısı

```
<Provider>Provider/
├── build.gradle.kts
└── src/
    ├── main/AndroidManifest.xml
    ├── main/kotlin/tr/<provider>/<Provider>.kt
    ├── main/kotlin/tr/<provider>/<Provider>Plugin.kt
    └── test/kotlin/tr/<provider>/<Provider>Test.kt
```

`AndroidManifest.xml` dosyası tek satırdır:

```xml
<?xml version="1.0" encoding="utf-8"?><manifest/>
```

### 2. Plugin giriş noktası

```kotlin
package tr.<provider>

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class <Provider>ProviderPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(<Provider>Provider())
    }
}
```

### 3. Provider sınıfı

`MainAPI`'yi genişletin ve ihtiyacınız olan metotları geçersiz kılın:

| Metot | Ne zaman |
|---|---|
| `getMainPage(page, request)` | Ana sayfa bölümleri |
| `search(query)` | Arama |
| `load(url)` | Detay sayfası |
| `loadLinks(data, ...)` | Oynatılabilir adres |

Oynatma için `ExtractorLink` ve `ExtractorLinkType.M3U8` kullanın:

```kotlin
callback.invoke(
    newExtractorLink(name, name, url) {
        type = ExtractorLinkType.M3U8
        quality = Qualities.Unknown.value
        referer = "$mainUrl/"
    }
)
```

### 4. Sürüm numarası

`build.gradle.kts` içindeki `version` değerini **her değişiklikte bir artırın**:

```kotlin
version = 1   →   version = 2
```

Cloudstream sürümleri önbelleğe alır; sürüm artmazsa kullanıcılarda
otomatik güncelleme tetiklenmez.

### 5. Kategoriler

`tvTypes` yalnızca Cloudstream'in enum değerlerini içermelidir
(`Live`, `Movie`, `TvSeries`, `Anime`, `Cartoon`, `AsianDrama`, `Others` …).
Sözleşmeyi `scripts/verify-provider-categories.sh` denetler; CI'da da çalışır.

### 6. Testler

Ayrıştırma mantığınızı `internal` fonksiyonlara taşıyın ve test edin —
böylece ağ olmadan doğrulanabilir olur:

```kotlin
internal fun parseSomething(html: String): Result? { ... }
```

```kotlin
class <Provider>Test {
    @Test
    fun parsesTitle() {
        assertEquals("Beklenen", parseSomething("<html>...</html>")?.title)
    }
}
```

Testler `common/src/test` altındakilerle birlikte her provider modülünde çalışır.

## Ortak kod

Birden fazla provider'ın ihtiyaç duyduğu kod `common/src/main/kotlin`
altına eklenir. Bu kaynaklar kök `build.gradle.kts` içindeki `sourceSets`
tanımı sayesinde **otomatik olarak her provider modülüne enjekte edilir** —
ekstra bir bağımlılık tanımlamanıza gerek yoktur.

## Ortak bölüm adları

Ana sayfa bölümlerinin adları tüm provider'larda **aynı** olmalıdır
(örneğin "Filmler", "Diziler"), böylece filtreler tutarlı çalışsın. Siteye
özgü bölümler standart bölümlerden **sonra** gelmelidir.

## Commit mesajı

```
<provider>: <değişiklik açıklaması>
```

Birden fazla provider'da değişiklik yaptıysanız:

```
exts: <değişiklik açıklaması>
```

## Derleme ve test

```bash
./gradlew <Provider>Provider:make
./gradlew testReleaseUnitTest
```
