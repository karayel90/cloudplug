<p align="center">
	<b>🇹🇷 Cloudstream Eklentileri (Türkçe İçerik)</b>
</p>

## 📖 Bu ne?

[Cloudstream 3](https://github.com/recloudstream/cloudstream) için Türkçe içerik
sağlayan eklenti deposu. Şu anda **tabii** canlı TV kanalları (TRT 1, TRT 2,
TRT Spor, TRT Haber, TRT Çocuk, TRT Belgesel, TRT Müzik, TRT Türk, TRT Kürdi,
TRT Arapî, TRT Avaz, TRT WORLD, TRT Genç, TRT eba ve tabii kanalları) destekleniyor.

* [Cloudstream deposu](https://github.com/recloudstream/cloudstream)
* [Cloudstream Wiki](https://cloudstream.miraheze.org/wiki/Main_Page)

## ⚙️ Kurulum

Uygulama → Ayarlar → Uzantılar → Depo Ekle kısmına şu bağlantıyı yapıştırın:

```
https://raw.githubusercontent.com/karayel90/cloudplug/refs/heads/main/repo.json
```

Eklentiler `builds` branch'inden dağıtılır; CI her push'ta `.cs3` dosyalarını ve
`plugins.json`'ı oraya yazar.

### Depoyu güncelleme

Depo `karayel90/cloudplug` altındadır. Değişiklik yapmak için:

```bash
git clone https://github.com/karayel90/cloudplug.git
cd cloudplug
./gradlew TabiiProvider:make      # yerel derleme
./gradlew testReleaseUnitTest     # testler
```

Push'tan sonra CI otomatik olarak `builds` branch'ini günceller.

> `build.gradle.kts` içindeki `setRepo(...)` çağrısı, CI'da `GITHUB_REPOSITORY`
> ortam değişkeni sayesinde doğru repo adresini kullanır. Yedek değer
> (`https://github.com/karayel90/cloudplug`) yalnızca **yerelde** üretilen
> `plugins.json` için geçerlidir; dağıtım için daima CI'ın ürettiği
> `builds/plugins.json` kullanılmalı.

## 🧩 Eklentiler

| Eklenti | Açıklama | Türler |
|---|---|---|
| **tabii** | tabii.com canlı TV kanalları | Live |

## 🔌 Nasıl çalışıyor?

tabii, kanal listesini sunucu tarafında sayfaya gömer. Her canlı kanal
sayfasının `__NEXT_DATA__` bloğu tüm kanalları ve medya adreslerini içerir;
eklenti bu bloğu okuyup kanalları listeler.

Yayın çözümlemesinde sırasıyla şunlar denenir:

1. parametresiz şifresiz HLS (doğrudan CDN — oturum bağımlılığı yok)
2. herhangi bir şifresiz HLS
3. şifresiz DASH

DRM'li (FairPlay / Widevine) kayıtlar atlanır.

Abonelik gerektiren tabii kanalları için CS3'ün provider credentials alanına
tabii oturum çerezinizi yazarsanız isteklerde o çerez gönderilir.

## 🛠 Geliştirme

Derleme:

```bash
# Windows
.\gradlew.bat TabiiProvider:make
# Linux / macOS
./gradlew TabiiProvider:make
```

Testler:

```bash
./gradlew testReleaseUnitTest
```

Yeni bir provider eklemek için `CONTRIBUTING.md` dosyasına bakın. Her provider
için şu dosyalar gerekir:

```
<Provider>Provider/
├── build.gradle.kts
└── src/
    ├── main/AndroidManifest.xml
    ├── main/kotlin/tr/<provider>/<Provider>.kt
    ├── main/kotlin/tr/<provider>/<Provider>Plugin.kt
    └── test/kotlin/tr/<provider>/<Provider>Test.kt
```

Ortak kod `common/src/main/kotlin` altındadır ve otomatik olarak her provider
modülüne enjekte edilir.

## 📄 Lisans

[GNU GPL v3](LICENSE) veya üzeri.
