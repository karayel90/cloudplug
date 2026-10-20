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

Uygulama → Ayarlar → Uzantılar → Depo Ekle kısmına aşağıdaki bağlantıyı yapıştırın
(kendi adınızı yazmanız zorunda değil, sadece bağlantıyı yazın):

```
https://raw.githubusercontent.com/OWNER/cloudstream-extensions-tr/refs/heads/master/repo.json
```

## 🚀 Yayınlamadan önce (2 dakika)

Depoyu kendi hesabınıza taşımak için **4 yerde** placeholder değiştirmeniz gerekiyor.
Hepsini tek komutla bulabilirsiniz:

```bash
grep -rn "GITHUB_OWNER\|GITHUB_REPO" . --include="*.kts" --include="*.json"
```

| Dosya | Satır | Değiştirilecek |
|---|---|---|
| `repo.json` | 6 | `GITHUB_OWNER` → kullanıcı adınız, `GITHUB_REPO` → repo adınız |
| `build.gradle.kts` | 30 | `setRepo(...)` içindeki aynı iki değer |
| `build.gradle.kts` | 31 | `authors = listOf("GITHUB_OWNER")` |
| `TabiiProvider/build.gradle.kts` | 14 | `authors = listOf("GITHUB_OWNER")` |

> `GITHUB_REPOSITORY` ortam değişkeni CI'da otomatik geldiği için, `setRepo(...)`
> satırındaki yedek değer yalnızca **yerelde** üretilen `plugins.json` için
> önemlidir. Dağıtım için daima CI'ın ürettiği `builds/plugins.json` kullanılmalı.

### GitHub'a yükleme

```bash
git init
git add -A
git commit -m "Türkçe Cloudstream eklentileri: tabii canlı TV provider"
git remote add origin https://github.com/<KULLANICI>/<REPO>.git
git push -u origin master
```

Ardından GitHub'da **`builds` branch'ini oluşturun** (boş bir commit ile).
CI her push'ta `.cs3` dosyalarını ve `plugins.json`'ı bu branch'e yazar; o
branch olmadan CI'ın `checkout ref: builds` adımı başarısız olur.

CI ilk çalıştıktan sonra kullanıcılar şu adresi CS3 → Ayarlar → Uzantılar →
Depo Ekle kısmına yapıştırarak eklentiyi kurar:

```
https://raw.githubusercontent.com/<KULLANICI>/<REPO>/refs/heads/master/repo.json
```

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

[GNU GPL v3](LICENSE) veya üzeri..
