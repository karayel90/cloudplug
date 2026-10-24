<p align="center">
	<b>🇹🇷 Cloudstream Eklentileri (Türkçe İçerik)</b>
</p>

## 📖 Bu ne?

[Cloudstream 3](https://github.com/recloudstream/cloudstream) için Türkçe içerik
sağlayan eklenti deposu. **tabii** canlı TV kanalları (TRT 1, TRT 2,
TRT Spor, TRT Haber, TRT Çocuk, TRT Belgesel, TRT Müzik, TRT Türk, TRT Kürdi,
TRT Arapî, TRT Avaz, TRT WORLD, TRT Genç, TRT eba ve tabii kanalları),
**Ace Stream** (P2P) kanalları (beIN SPORTS HD 1-3 [TR]) ve **TR Kanalları**
(topluluk M3U listesinden otomatik güncellenen Türkçe canlı kanallar:
TV8, ATV, Star TV, DMAX, Kanal D, Show TV, Fox ve daha fazlası)
destekleniyor.

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
| **TR Kanalları** | Topluluk M3U listesindeki Türkçe canlı kanallar (TV8, ATV, Star TV, DMAX, Kanal D, Show TV, Fox...) | Live |

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

tabii ve Ace Stream satırları da TR Kanalları ile aynı logo düzenini kullanır:
logolar `logos/tabii/` ve `logos/ace/` altında kart oranına sığdırılmış hâlde
tutulur, koyu logolar açık plakalıdır (bkz. aşağıdaki logo notu).

### TR Kanalları (M3U listesi)

Türkçe canlı kanallar topluluk tarafından korunan bir **M3U listesinden**
otomatik çekilir. Kanal kataloğu eklenti içinde sabit değildir; çalışma anında
listeden okunur. Böylece yayın adresleri yayıncı CDN'lerinde değiştiğinde
eklenti güncellemesi gerekmez.

- Kaynak liste: [omerdenizhan/IPTV-M3U](https://github.com/omerdenizhan/IPTV-M3U)
  (`m3u/turkiye-iptv-org.m3u`)
- Liste her okunuşta **çift kanal temizliğinden** geçer: aynı kanalın birden
  fazla çözünürlükteki kayıtlarından **en yüksek çözünürlüklü** olanı kullanılır
  (örn. "ATV (360p)" + "ATV (1080p)" → yalnızca 1080p kalır).
- Bazı kanalların yayın adresi özel `User-Agent` ya da `Referer` başlığı
  bekler; listede bu değerler varsa oynatma anında gönderilir.
- Bilinen Daion CDN kanalları (TV8, Star TV, DMAX, NTV, Kanal D) için listede
  referer yoksa kanalın kendi sitesi `Referer` olarak eklenir; bu CDN'ler
  başlıksız isteği reddediyor.
- Kanallar ana sayfada **izlenme popülerliğine** göre sıralanır (ulusal
  yayıncılar üstte); sıralama `TrLiveChannels.POPULARITY` listesinden tek
  yerden düzenlenebilir. Listede olmayan kanallar sona, alfabetik sırayla
  eklenir.
- Yayın vermeyen adresler `DeadStreams` kara listesinde tutulur ve katalog
  oluşturulurken atlanır; böylece açılıp hata veren kanallar görünmez.
  Filtre adres bazlıdır: liste aynı kanal için yeni bir adres yayınladığında
  kanal eklenti güncellemesi gerekmeden geri gelir. Kanalın ölü 1080p kaydı
  varsa çalışan daha düşük çözünürlüklü kaydı kullanılır.
- Ana sayfa satırı **yatay kartlarla** çizilir ve kanal logoları kendi
  depomuzdan servis edilir (`logos/`). Cloudstream kart görselini `centerCrop`
  ile çizdiği için ham logolar — oranları 0.86 ile 4.16 arasında değişiyor —
  kenarlardan kırpılıyordu. `tools/build-logos.ps1` her logoyu şeffaf bir kare
  tuvalin ortasına yerleştirir; kare tuval hem yatay hem dikey kartta tam
  göründüğü için logo hiçbir düzende kesilmez. Koyu logoların arkasına, koyu
  temada kaybolmaması için açık renkli yuvarlak bir plaka eklenir. Listede
  olmayan yeni kanallar ham `tvg-logo` adresine düşer; logo üretmek için:
  `pwsh -File tools/build-logos.ps1` (betik sonunda `tools/optimize-logos.py`
  çalışır ve PNG'leri 256 renge indirir).
- Topluluk listesinde hiç bulunmayan **Show TV, Beyaz TV, TV2, DMAX ve TLC**,
  yayıncıların kendi sitelerindeki resmi HLS adresleriyle `ExtraChannels`
  üzerinden eklenir. TV2, editoryal sıralamada CNBC-e'nin hemen ardında yer
  alır. NTV listede zaten çalışan bir kayıtla mevcuttur.

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

**v26 davranışları:**

- **Otomatik motor başlatma:** motor kapalıysa ve Ace Stream uygulaması
  kuruluysa eklenti uygulamayı kendisi açar ve motorun ayağa kalkmasını
  üstel bekleme ile (üst sınır ~20 sn) bekler; motor açılmazsa "birkaç
  saniye bekleyip tekrar dokunun" mesajı gösterilir.
- **Çalışkan referans önbelleği:** doğrulanan çalışan referans 15 dakika
  bellekte tutulur; kanalın sonraki açılışında tek salt-okunur istekle
  kontrol edilir, tam arama taraması yapılmaz.
- **Kısa zaman aşımları:** motor durum sondajı 3 sn, içerik doğrulama 8 sn,
  arama dizini 12 sn; motor yanit vermezse dakikalarca kilitlenme olmaz.
- **Eszamanlı doğrulama:** ilk 3 aday referans paralel doğrulanır, sonuç
  öncelik sırasına göre tüketilir; kalani sirayla. Dogrulama yine
  salt-okunur ucuz yapilir ve oynatma oturumu acmaz.
- **Doğrulanmış referans devri:** `acestream://` devrinde URL'deki sabit
  kimlik yerine doğrulanan çalışan referansın değeri gönderilir.
- **Deneysel:** Ace Player uygulaması kurulu olmayan (yalnızca motoru olan)
  cihazlarda, motor manifest'i CS3'e `VIDEO` tipiyle verilir; böylece CS3'ün
  ikinci manifest isteği atlayan önizleme yükleyicisi devre dışı kalır ve
  `HTTP 500` çift oturum hatası önlenir. Yayın için motor hâlâ şarttır.

Content ID'ler yayıncı dosyayı yeniden yüklediğinde değişebildiği için,
oynatma anında motorun arama dizininden güncel kimlikler de bulunur ve
sırayla denenir; eklendeki sabit kimlikler yalnızca yedektir. Arama ad
eslesmesi esnektir: hedefin TUM kelimelerini kapsayan adaylar kabul
edilir, fazla kelimeler (HD/TR) siralamada cezalanir.

### Performans notları

- Katalog (M3U) ve tabii kanal listesi bellekte tutulur; ana sayfa, arama ve
  oynatma aynı listeyi paylaşır. Süre dolduğunda liste **tek sefer** yenilenir
  (`Mutex`): ana sayfa, arama ve oynatma aynı anda istese bile ikinci bir
  indirme başlamaz.
- Yayın açılırken katalog yenilenmez; `byUrl()` / `channelByUrl()` yalnızca
  bellekteki haritaya bakar. Böylece 5 dakikalık önbellek süresi dolsa bile
  kanal değiştirme ağ beklemez. Ağ hatasında son başarılı liste korunur.
- Hızlı arama her tuş vuruşunda çalışır ve süresi dolmuş listeyi yenilemez;
  yazarken sonuçlar ağ beklemesiyle kesilmez. Yenileme ana sayfa açılışında
  yapılır.
- Kanal adı normalizasyonu kayıt başına bir kez hesaplanır (`Entry.normalizedTitle`);
  popülerlik sırası ve arama tek harita aramasına indirgenmiştir.
- Kart logoları 256 renkli PNG'dir (`tools/optimize-logos.py`): 157 dosya
  ~6,9 MB yerine ~1,2 MB. İlk açılışta ve kaydırmada daha az veri iner.

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
