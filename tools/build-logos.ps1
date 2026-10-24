# Kanal logolarini kartlara sigdirir.
#
# Cloudstream kartlari `centerCrop` ile ciziyor: logo kendi oranindan farkli
# bir kutuya konunca kenarlardan kirpiliyor. Bu betik her logoyu seffaf bir
# kare tuvale, ortadaki guvenli alana yerlestirir. Kare tuval hem yatay
# (180x114) hem dikey (114x180) kartta merkezden kirpildigi icin logo iki
# duzende de tam gorunur.
#
# Kullanim:  pwsh -File tools/build-logos.ps1
# Cikti:     logos/<kanal>.png  +  TabiiProvider/.../LogoAssets.kt
#
# Kanal listesi M3U'dan, olu adresler DeadStreams.kt'den, elle eklenen
# kanallar asagidaki $extras listesinden gelir; ExtraChannels.kt degisirse
# burasi da guncellenmeli.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'logos'
$assetsKt = Join-Path $root 'TabiiProvider/src/main/kotlin/tr/tabii/LogoAssets.kt'
$tmpDir = Join-Path $env:TEMP 'cloudplug-logos'
New-Item -ItemType Directory -Force -Path $outDir, $tmpDir | Out-Null

$canvas = 512      # kare tuval
$safe = 304        # logonun sigacagi merkez kutu (tuvalin %59'u; kart kirpmasi %63)
$m3uUrl = 'https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye-iptv-org.m3u'

function Norm([string]$raw) {
    $s = $raw -replace '\([^)]*\)', ' ' -replace '\[[^\]]*\]', ' '
    $s = $s.ToLowerInvariant()
    $s = $s.Replace([char]0x0131, 'i').Replace('ş', 's').Replace('ğ', 'g').Replace('ü', 'u').Replace('ö', 'o').Replace('ç', 'c')
    return ($s -replace '[^a-z0-9]', '')
}
function Resolution([string]$title) {
    $l = $title.ToLowerInvariant()
    if ($l.Contains('8k')) { return 4320 }
    if ($l.Contains('4k') -or $l.Contains('2160p')) { return 2160 }
    $m = [regex]::Match($l, '(\d{3,4})p'); if ($m.Success) { return [int]$m.Groups[1].Value }
    return 0
}
function Save-Image($img, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($canvas, $canvas)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $scale = [Math]::Min($safe / $img.Width, $safe / $img.Height)
    $w = [int][Math]::Round($img.Width * $scale)
    $h = [int][Math]::Round($img.Height * $scale)
    $x = [int](($canvas - $w) / 2)
    $y = [int](($canvas - $h) / 2)
    $g.DrawImage($img, $x, $y, $w, $h)
    $g.Dispose()

    # Logonun kendi pikselleri koyuysa koyu kart uzerinde kaybolur; arkasina
    # acik renkli yuvarlak bir plaka koyariz. Tam dolu (fotograf gibi) koyu
    # kisimlarin kendi arka plani oldugu icin onlara dokunmayiz.
    $minX = $canvas; $minY = $canvas; $maxX = -1; $maxY = -1
    $lumSum = 0.0; $visible = 0; $opaqueInBox = 0; $boxPixels = 0
    for ($py = 0; $py -lt $canvas; $py += 2) {
        for ($px = 0; $px -lt $canvas; $px += 2) {
            $c = $bmp.GetPixel($px, $py)
            if ($c.A -le 40) { continue }
            if ($px -lt $minX) { $minX = $px }; if ($px -gt $maxX) { $maxX = $px }
            if ($py -lt $minY) { $minY = $py }; if ($py -gt $maxY) { $maxY = $py }
            $visible++
            $lumSum += (0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B) / 255.0
        }
    }
    if ($visible -eq 0) { $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose(); return }
    $lum = $lumSum / $visible
    for ($py = $minY; $py -le $maxY; $py += 2) {
        for ($px = $minX; $px -le $maxX; $px += 2) {
            $boxPixels++
            if ($bmp.GetPixel($px, $py).A -gt 40) { $opaqueInBox++ }
        }
    }
    $fill = $opaqueInBox / [Math]::Max(1, $boxPixels)

    if ($lum -lt 0.50 -and $fill -lt 0.90) {
        $pad = 16
        $rx = [Math]::Max(0, $minX - $pad); $ry = [Math]::Max(0, $minY - $pad)
        $rw = [Math]::Min($canvas, $maxX + $pad) - $rx
        $rh = [Math]::Min($canvas, $maxY + $pad) - $ry
        $out = New-Object System.Drawing.Bitmap($canvas, $canvas)
        $g2 = [System.Drawing.Graphics]::FromImage($out)
        $g2.Clear([System.Drawing.Color]::Transparent)
        $g2.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $r = 26
        $pathGeo = New-Object System.Drawing.Drawing2D.GraphicsPath
        $pathGeo.AddArc($rx, $ry, $r * 2, $r * 2, 180, 90)
        $pathGeo.AddArc($rx + $rw - $r * 2, $ry, $r * 2, $r * 2, 270, 90)
        $pathGeo.AddArc($rx + $rw - $r * 2, $ry + $rh - $r * 2, $r * 2, $r * 2, 0, 90)
        $pathGeo.AddArc($rx, $ry + $rh - $r * 2, $r * 2, $r * 2, 90, 90)
        $pathGeo.CloseFigure()
        $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 245, 245))
        $g2.FillPath($brush, $pathGeo)
        $g2.DrawImage($bmp, 0, 0)
        $g2.Dispose(); $brush.Dispose(); $pathGeo.Dispose()
        $bmp.Dispose()
        $bmp = $out
    }

    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
function Try-Load([string]$path) {
    try { return [System.Drawing.Image]::FromFile($path) } catch { return $null }
}
function Download([string]$url, [string]$path) {
    & curl.exe -sS -L --max-time 30 -A 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130 Safari/537.36' -o $path $url 2>$null | Out-Null
}

# --- katalog ---
$lines = (& curl.exe -sSL --max-time 60 $m3uUrl) -split "`r?`n"
$deadSrc = Get-Content (Join-Path $root 'TabiiProvider/src/main/kotlin/tr/tabii/DeadStreams.kt') -Raw
$dead = [System.Collections.Generic.HashSet[string]]::new()
[regex]::Matches($deadSrc, '"(https?://[^"]+)"') | ForEach-Object { [void]$dead.Add($_.Groups[1].Value) }

$entries = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch '^#EXTINF') { continue }
    $title = ($lines[$i] -split ',', 2)[1]
    if ($null -eq $title) { continue }
    $title = $title.Trim()
    $logo = [regex]::Match($lines[$i], 'tvg-logo="([^"]*)"').Groups[1].Value
    $j = $i + 1
    while ($j -lt $lines.Count -and ($lines[$j] -eq '' -or $lines[$j].StartsWith('#'))) { $j++ }
    if ($j -ge $lines.Count) { continue }
    $entries.Add([PSCustomObject]@{ Title = $title; Url = $lines[$j].Trim(); Logo = $logo; Order = $entries.Count })
    $i = $j
}

# ExtraChannels.kt ile ayni (elle eklenen kanallar)
$extras = @(
    @{ T = 'Show TV (1080p)'; L = 'https://www.showtv.com.tr/favicon.ico' },
    @{ T = 'Beyaz TV (1080p)'; L = 'https://www.beyaztv.com.tr/favicon.ico' },
    @{ T = 'TV2'; L = 'https://www.google.com/s2/favicons?domain=tv2.com.tr&sz=128' },
    @{ T = 'DMAX (720p)'; L = 'https://www.dmax.com.tr/favicon.ico' },
    @{ T = 'TLC (720p)'; L = 'https://www.google.com/s2/favicons?domain=tlc.com.tr&sz=128' }
)
foreach ($x in $extras) { $entries.Add([PSCustomObject]@{ Title = $x.T; Url = "extra://$($x.T)"; Logo = $x.L; Order = 100000 }) }

$alive = @($entries | Where-Object { -not $dead.Contains($_.Url) })
$best = [ordered]@{}
foreach ($e in $alive) {
    $k = Norm $e.Title
    if (-not $k) { continue }
    if (-not $best.Contains($k)) { $best[$k] = $e; continue }
    $cur = $best[$k]
    $a = Resolution $e.Title; $b = Resolution $cur.Title
    if ($a -ne $b) { if ($a -gt $b) { $best[$k] = $e }; continue }
    if ($e.Url.StartsWith('https://') -and -not $cur.Url.StartsWith('http://')) { $best[$k] = $e }
}
$channels = @($best.Values)

# logo bulunamayan kanallar icin elle yedek
$overrides = @{
    'flashhabertv' = @('https://www.google.com/s2/favicons?domain=flashhabertv.com&sz=128',
                       'https://www.google.com/s2/favicons?domain=flashhaber.com&sz=128')
    'dosttv'       = @('https://www.google.com/s2/favicons?domain=dosttv.com&sz=128')
    'dmax'         = @('https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4f/DMAX_-_Logo_2016.svg/960px-DMAX_-_Logo_2016.svg.png')
}

$ok = 0; $failed = @()
$keys = New-Object System.Collections.Generic.List[string]
foreach ($c in $channels) {
    $key = Norm $c.Title
    $srcs = @()
    if ($c.Logo -and $c.Logo.StartsWith('http')) { $srcs += $c.Logo }
    if ($overrides.ContainsKey($key)) { $srcs += $overrides[$key] }
    if ($c.Logo -and $c.Logo.StartsWith('http')) {
        try { $srcs += "https://www.google.com/s2/favicons?domain=$(([uri]$c.Logo).Host)&sz=128" } catch { }
    }
    $img = $null
    foreach ($src in $srcs) {
        $tmp = Join-Path $tmpDir ($key + '_' + [guid]::NewGuid().ToString('N').Substring(0, 6))
        Download $src $tmp
        $img = Try-Load $tmp
        if ($img -and $img.Width -ge 32 -and $img.Height -ge 32) { break }
        if ($img) { $img.Dispose(); $img = $null }
    }
    if (-not $img) { $failed += $c.Title; continue }
    Save-Image $img (Join-Path $outDir "$key.png")
    $img.Dispose()
    $keys.Add($key)
    $ok++
}

"Uretilen logo: $ok / $($channels.Count)"
if ($failed.Count -gt 0) { "Logo bulunamayanlar: " + ($failed -join ', ') }

# --- LogoAssets.kt ---
$keyLines = ($keys | ForEach-Object { '        "' + $_ + '",' }) -join "`n"
$kt = @"
package tr.tabii

/**
 * Kendi barındırdığımız, kart oranına sığdırılmış kanal logoları.
 *
 * Cloudstream kartları görseli `centerCrop` ile çiziyor. Kanal logolarının
 * oranları çok farklı olduğu için (0.86 ile 4.16 arası) ham logo ya yanlardan
 * ya üstten/alttan kesiliyordu. [tools/build-logos.ps1] her logoyu şeffaf bir
 * kare tuvale, ortadaki güvenli alana yerleştirir; kare tuval hem yatay hem
 * dikey kartta kırpılmadan göründüğü için logo her iki düzende de tam çıkar.
 *
 * Listede olmayan (ör. M3U'ya sonradan eklenen) kanallar ham tvg-logo
 * adresine düşer; yeni kanal için logo üretmek üzere betik yeniden çalıştırılır.
 */
internal object LogoAssets {

    private const val BASE =
        "https://raw.githubusercontent.com/karayel90/cloudplug/main/logos/"

    /** `logos/` altında dosyası bulunan kanalların düzleştirilmiş adları. */
    private val KEYS: Set<String> = setOf(
$keyLines
    )

    /**
     * Kanalın poster adresini döndürür: hazır logo varsa kendi depomuzdan,
     * yoksa M3U'nun verdiği ham adres.
     */
    fun posterFor(key: String, fallback: String?): String? =
        if (key in KEYS) BASE + key + ".png" else fallback
}
"@
Set-Content -Path $assetsKt -Value $kt -Encoding UTF8
"LogoAssets.kt yazildi: $($keys.Count) anahtar"


