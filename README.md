<p align="center">
	<b>🇹🇷 Cloudstream Eklentileri (Türkçe İçerik)</b>
</p>

## 📖 Bu ne?

[Cloudstream 3](https://github.com/recloudstream/cloudstream) için Türkçe içerik
sağlayan eklenti deposu. **tabii** canlı TV kanalları (TRT 1, TRT 2,
TRT Spor, TRT Haber, TRT Çocuk, TRT Belgesel, TRT Müzik, TRT Türk, TRT Kürdi,
TRT Arapî, TRT Avaz, TRT WORLD, TRT Genç, TRT eba ve tabii kanalları),
**Ace Stream** (P2P) kanalları (beIN SPORTS HD 1-3 [TR]) ve **TR Kanalları**
(TV8, ATV, Star TV, DMAX) destekleniyor.

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

Depoda **tek bir eklenti** (`tabii`) var. Eklenti içinde iki sağlayıcı
kayıtlı; CS3 ana sayfasında iki sekme olarak görünürler:

| Sağlayıcı | Kanal grubu | Türler |
|---|---|---|
| **tabii** | tabii.com canlı TV kanalları | Live |
| **tabii** | Ace Stream (P2P) kanalları: beIN SPORTS HD 1-3 [TR] | Live |
| **TR Kanalları** | TV8, ATV, Star TV (STARTV), DMAX canlı yayın kanalları | Live |

## 🔌 Nasıl çalışıyor?

### tabii kanalları

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

### TR Kanalları (TV8, ATV, Star TV, DMAX)

Bu dört yayıncı yayınlarını **Daion CDN** üzerinden HLS olarak sunar. Adres
şablonu kanal başına sabittir:

```
https://<cdn>/<kanal>/<kanal>.m3u8?app=<app-uuid>&ce=3
```

Master playlist her istekte kendini yenileyen `sid` jetonları üretir; kanal
listesi tek bir `app` kimliğiyle sabit olduğu için eklenti listeyi bir kez
okuyup sabit tutar.

| Kanal | Manifest |
|---|---|
| TV8 | `tv8.daioncdn.net/tv8/tv8.m3u8` |
| ATV | `trkvz.daioncdn.net/atv/atv.m3u8` |
| Star TV | `dogus.daioncdn.net/startv/startv.m3u8` |
| DMAX | `dogus.daioncdn.net/dmax/dmax.m3u8` |

#### ATV: "secure token" gereksinimi

ATV'in yayın adresi kendiliğinden oynatılamaz; adres yayıncının kendi
servisinde imzalanmalıdır. Eklenti oynatma anında şu adresi çağırır:

```
GET https://securevideotoken.tmgrup.com.tr/webtv/secure?<rastgele>&url=<imzalanacak adres>
X-isApp: false
X-Rand: <epoch ms>
Origin: https://www.atv.com.tr
Referer: https://www.atv.com.tr/
```

Yanıttaki `Url` alanı `st` (başlangıç jetonu) ve `e` (bitiş zamanı)
parametreleri eklenmiş nihai adresi verir. Jetonun ömrü ~11,5 saattir, bu
yüzden her oynatmada tazelenir.

> **`Origin` ve `Referer` başlıkları zorunludur** — bu ikisi olmadan servis
> `403` döner.

CDN istekleri `Referer` başlığı olmadan da reddedilir; eklenti her kanalın
kendi sitesini `Referer` olarak gönderir.

### Ace Stream kanalları

Ana sayfadaki **Ace Stream** bölümü, tabii'de olmayan ucretli kanalları
(beIN SPORTS HD 1-3 [TR]) P2P ağı üzerinden sunar.

Ace Stream kanalları 40 haneli bir **content ID** ile adreslenir
(`acestream://<content-id>`). İki farklı kimlik vardır ve motor bunları
birbirinin yerine kullanmaz: `content_id` ve `infohash`.

| Kanal | Content ID |
|---|---|
| beIN SPORTS HD 1 [TR] | `118416583faa3fb4f03800173429b6b844b43a52` |
| beIN SPORTS HD 2 [TR] | `48bee10b883f17c7186d60f1e8022dca0ba41a05` |
| beIN SPORTS HD 3 [TR] | `ed81c333815247536edfcdb2755147e8c4144d62` |

**Yayın neden Ace Stream uygulamasına bırakılıyor?** Motorun **tek bir
oynatıcısı** vardır. Yayın CS3'te oynatılırsa aynı anda iki istek atılır
(bir tanesi önizleme, bir tanesi oynatma) ve ikisi de birer oynatma oturumu
açar; motor iki oturuma birden açık kalamayınca `HTTP 500` döner. Bunun
yerine `acestream://` adresi doğrudan **Ace Stream uygulamasına** gönderilir
— yayını zaten onun motoru çeker, CS3 motorun HTTP ucuna hiç dokunmaz.

**Gereksinim:** cihazda [Ace Stream](https://www.acestream.org/) kurulu ve
çalışıyor olmalıdır. **Motor yalnızca uygulama açıkken ayakta kalır**;
uygulama arka planda kapatılırsa motor durur ve eklenti net bir mesajla
uyarır.

Content ID'ler yayıncı dosyayı yeniden yüklediğinde değişebildiği için,
oynatma anında motorun arama dizininden güncel kimlikler de bulunur ve
sırayla denenir; eklendeki sabit kimlikler yalnızca yedektir.

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

Bir modül birden fazla provider barındırabilir: her `MainAPI` eklenti giriş
noktasında `registerMainAPI(...)` ile kaydedilir. Mevcut modül iki sağlayıcı
barındırıyor (`TabiiProvider` ve `TrLiveProvider`).

> Modül ya da eklenti adı değiştirilmemelidir. CS3 kurulu eklentileri
> `internalName` ile tanır; ad değişirse eski sürüm kullanıcıda kalır ve
> yeni sürüm yan yana kurulur.

## 📄 Lisans

[GNU GPL v3](LICENSE) veya üzeri.
