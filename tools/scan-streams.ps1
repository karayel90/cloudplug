<#
.SYNOPSIS
    andro.evrenesoglu<N>.click makine havuzunu tarar ve DirectStreams.kt
    icindeki calisan makine listesini yeniler.

.DESCRIPTION
    Havuz dort asamada taranir:

      0. **DNS-on-HTTPS on tarayisi:** her numaranin A kaydi dns.google
         uzerinden sorulur. Kaydi OLMAYAN numaralara HTTP isteği atilmaz;
         boylece yarisi bos is olan bir tarama hizlanir. (Dogrulama
         sirasinda yalnizca public DNS'de kayitli makineler aday olur.)
      1. **Yavas tarama:** kayitli adaylara tek bir ornek kanal istegi
         atilir; HTTP hatasi / sertifika gecersiz / calisiyor olarak
         siniflandirilir.
      2. **Kanal yolu dogrulamasi:** calisan gorunen makinelerde 12 kanal
         yolunun TAMAMI ayri ayri dogrulanir.
      3. **Segment kontrolu:** dogrulanmis makinelerden ilk kac tanesinde
         ilk video segmenti cekilip MPEG-TS senkron bayti (0x47) kontrol
         edilir. Tum makineler AYNI arka ucgagi paylastigi icin spot
         kontrol yeterlidir (-SegmentCheckLimit).

    Sonunda `DirectStreams.kt` icindeki `scan-streams` blogu yeniden
    yazilir. Eski liste ile YENI LISTE ayni ise tarih de degistirilmez;
    boylece CI her calismada bos commit atmaz.

.PARAMETER From
    Tarama araliginin basi (varsayilan 185).
.PARAMETER To
    Tarama araliginin sonu (varsayilan 330).
.PARAMETER ProviderFile
    Uzerindeki blogu yenilenecek DirectStreams.kt dosyasi.
.PARAMETER ThrottleLimit
    Eszamanli istek sayisi.
.PARAMETER SegmentCheckLimit
    Segment kontrolu yapilacak en fazla makine sayisi (spot kontrol).
.PARAMETER NoWrite
    Yalnizca rapor verir, dosyayi degistirmez.

.EXAMPLE
    pwsh tools/scan-streams.ps1
    pwsh tools/scan-streams.ps1 -From 185 -To 400 -NoWrite
#>
[CmdletBinding()]
param(
    [int]$From = 185,
    [int]$To = 330,
    [string]$ProviderFile = (Join-Path $PSScriptRoot ".." "TabiiProvider" "src" "main" "kotlin" "tr" "tabii" "DirectStreams.kt"),
    [int]$ThrottleLimit = 12,
    [int]$SegmentCheckLimit = 5,
    [switch]$NoWrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Kanal yollari: DirectStreams.CHANNELS ile birebir ayni sirada olmali.
$ChannelPaths = [ordered]@{
    "bein1"      = "checklist/androstreamlivebs1.m3u8"
    "bein2"      = "checklist/androstreamlivebs2.m3u8"
    "bein3"      = "checklist/androstreamlivebs3.m3u8"
    "bein4"      = "checklist/androstreamlivebs4.m3u8"
    "bein5"      = "checklist/androstreamlivebs5.m3u8"
    "ssport"     = "checklist/androstreamlivess1.m3u8"
    "ssport2"    = "checklist/androstreamlivess2.m3u8"
    "ssportplus" = "checklist/androstreamlivessplus1.m3u8"
    "tivibu1"    = "checklist/androstreamlivets1.m3u8"
    "tivibu2"    = "checklist/androstreamlivets2.m3u8"
    "tivibu3"    = "checklist/androstreamlivets3.m3u8"
    "tivibu4"    = "checklist/androstreamlivets4.m3u8"
}

$UserAgent = "Mozilla/5.0 (Linux; Android 13) ExoPlayer/2.19.1"
$TimeoutSec = 8

function Get-Host([int]$n) { "andro.evrenesoglu$n.click" }

# --- 0) DNS-on-HTTPS on tarayisi -------------------------------------------
Write-Host "== 0/4 DNS-on-HTTPS on tarayisi: $From..$To ==" -ForegroundColor Cyan
$numbers = $From..$To
$dnsState = $numbers | ForEach-Object -Parallel {
    $n = $_
    $name = "andro.evrenesoglu$n.click"
    # Iki bagimsiz DoH saglayicisi: biri engelli/takiliysa digeri devreye girer.
    foreach ($endpoint in @(
        "https://dns.google/resolve?name=$name&type=A",
        "https://cloudflare-dns.com/dns-query?name=$name&type=A"
    )) {
        try {
            $r = Invoke-RestMethod -Uri $endpoint -Headers @{ Accept = 'application/dns-json' } -TimeoutSec 4
            return [pscustomobject]@{
                N = $n
                HasRecord = (@($r.Answer | Where-Object { $_.type -eq 1 }).Count -gt 0)
            }
        } catch { continue }
    }
    # Ikisi de basarisiz: adayi koru, HTTP asamasi gercekten dener.
    [pscustomobject]@{ N = $n; HasRecord = $true; DohError = $true }
} -ThrottleLimit $ThrottleLimit

$dnsMissing = @($dnsState | Where-Object { -not $_.HasRecord } | ForEach-Object { $_.N })
$candidates = @($dnsState | Where-Object HasRecord | ForEach-Object { $_.N })
Write-Host ("  DNS kaydi olan: {0} · olmayan (atlandi): {1}" -f $candidates.Count, $dnsMissing.Count)
if ($dnsMissing.Count -gt 0) {
    Write-Host ("  atlanan numaralar: " + ($dnsMissing -join ", ")) -ForegroundColor DarkGray
}
if (-not $candidates) {
    Write-Error "Verilen aralikta DNS kaydi olan numara yok."
    exit 1
}

# --- 1) Yavas tarama --------------------------------------------------------
Write-Host "== 1/4 Yavas tarama (HTTP) ==" -ForegroundColor Cyan
$stage1 = $candidates | ForEach-Object -Parallel {
    $n = $_
    $h = $using:UserAgent
    $t = $using:TimeoutSec
    $hostName = "andro.evrenesoglu$n.click"
    try {
        $r = Invoke-WebRequest -Uri "https://$hostName/checklist/androstreamlivebs1.m3u8" `
            -UserAgent $h -TimeoutSec $t -SkipHttpErrorCheck
        if ($r.StatusCode -eq 200 -and $r.Content.StartsWith("#EXTM3U")) {
            [pscustomobject]@{ N = $n; State = "alive"; Detail = "HTTP 200" }
        } else {
            [pscustomobject]@{ N = $n; State = "dead"; Detail = "HTTP $($r.StatusCode)" }
        }
    } catch {
        $msg = $_.Exception.Message
        $state = if ($msg -match "certificate|SSL|TLS|trust") { "cert" }
                 elseif ($msg -match "not find|resolve|No such host") { "dns" }
                 else { "error" }
        [pscustomobject]@{ N = $n; State = $state; Detail = $msg.Substring(0, [Math]::Min(60, $msg.Length)) }
    }
} -ThrottleLimit $ThrottleLimit | Sort-Object N

$alive = @($stage1 | Where-Object State -eq "alive")
$stage1 | Group-Object State | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0,-6} {1}" -f $_.Name, ($_.Group.N -join ", "))
}
if (-not $alive) {
    Write-Error "Havuzda calisan makine bulunamadi."
    exit 1
}

# --- 2) Kanal yolu dogrulamasi ---------------------------------------------
Write-Host "== 2/4 Kanal yolu dogrulamasi ($($alive.Count) aday) ==" -ForegroundColor Cyan
$stage2 = $alive | ForEach-Object -Parallel {
    $n = $_.N
    $h = $using:UserAgent
    $t = $using:TimeoutSec
    $paths = $using:ChannelPaths
    $hostName = "andro.evrenesoglu$n.click"
    $missing = @()
    foreach ($key in $paths.Keys) {
        try {
            $r = Invoke-WebRequest -Uri "https://$hostName/$($paths[$key])" `
                -UserAgent $h -TimeoutSec $t -SkipHttpErrorCheck
            if (-not ($r.StatusCode -eq 200 -and $r.Content.StartsWith("#EXTM3U"))) {
                $missing += "$key(HTTP $($r.StatusCode))"
            }
        } catch {
            $missing += "$key(hata)"
        }
    }
    [pscustomobject]@{ N = $n; Verified = ($missing.Count -eq 0); Missing = ($missing -join "; ") }
} -ThrottleLimit $ThrottleLimit | Sort-Object N

$verified = @($stage2 | Where-Object Verified)
$partial = @($stage2 | Where-Object { -not $_.Verified })
foreach ($p in $partial) {
    Write-Warning "Makine $($p.N) kismen calisiyor, elendi: $($p.Missing)"
}
if (-not $verified) {
    Write-Error "12 kanal yolunun tamamini sunan makine yok."
    exit 1
}

# --- 3) Segment kontrolu (spot) ---------------------------------------------
Write-Host "== 3/4 Segment kontrolu (MPEG-TS 0x47, ilk $SegmentCheckLimit makine) ==" -ForegroundColor Cyan
$probePath = $ChannelPaths["bein1"]
$checked = 0
foreach ($v in ($verified | Select-Object -First $SegmentCheckLimit)) {
    $hostName = Get-Host $v.N
    try {
        $mp = Invoke-WebRequest -Uri "https://$hostName/$probePath" -UserAgent $UserAgent -TimeoutSec $TimeoutSec
        $segLine = ($mp.Content -split "`n") | Where-Object { $_ -and -not $_.StartsWith("#") } | Select-Object -First 1
        $baseUri = [Uri]"https://$hostName/$probePath"
        $segUrl = [Uri]::new($baseUri, $segLine.Trim()).AbsoluteUri
        $tmp = New-TemporaryFile
        Invoke-WebRequest -Uri $segUrl -UserAgent $UserAgent -TimeoutSec $TimeoutSec -OutFile $tmp
        $first = [System.IO.File]::ReadAllBytes($tmp)[0]
        Remove-Item $tmp -Force
        if ($first -ne 0x47) {
            Write-Warning "Makine $($v.N): segment MPEG-TS degil (ilk bayt 0x$($first.ToString('X2'))), elendi."
            $v.Verified = $false
        } else {
            Write-Host ("  {0,-28} segment OK (0x47)" -f $hostName) -ForegroundColor Green
        }
        $checked++
    } catch {
        Write-Warning "Makine $($v.N): segment kontrolu basarisiz ($($_.Exception.Message)), elendi."
        $v.Verified = $false
    }
}
$verified = @($verified | Where-Object Verified)
if (-not $verified) {
    Write-Error "Segment kontrolunden gecen makine yok."
    exit 1
}
Write-Host "  dogrulanmis makine: $($verified.Count) (segment kontrol edilen: $checked)" -ForegroundColor Green

# --- DirectStreams.kt blogunu yenile ---------------------------------------
if (-not (Test-Path $ProviderFile)) {
    Write-Host "HATA: Dosya bulunamadi: $ProviderFile" -ForegroundColor Red
    exit 2
}
$raw = Get-Content -Raw $ProviderFile
$pattern = '(?s)(?<=// >>> scan-streams:BEGIN\r?\n).*?(?=// <<< scan-streams:END)'
$blockMatch = [regex]::Match($raw, $pattern)
if (-not $blockMatch.Success) {
    Write-Host "HATA: scan-streams blogu bulunamadi: $ProviderFile" -ForegroundColor Red
    exit 2
}

# Onceki birincil ve liste (degisiklik yoksa tarih korunur).
$prevPrimary = $null
if ($raw -match 'const val PRIMARY_HOST = "(?<h>[^"]+)"') { $prevPrimary = $Matches.h }
$prevScan = $null
if ($raw -match 'const val LAST_SCAN = "(?<d>[^"]+)"') { $prevScan = $Matches.d }

# Yalnizca PREFERRED_HOSTS listesini hedefle; PRIMARY_HOST satirini karistirma.
# (Blok genelinde ararsak 217 iki kez yakalanir ve liste hep "degismis" gorunur.)
$listMatch = [regex]::Match($blockMatch.Value,
    'val PREFERRED_HOSTS: List<String> = listOf\((?<list>.*?)\)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $listMatch.Success) {
    Write-Host "HATA: PREFERRED_HOSTS listesi bulunamadi: $ProviderFile" -ForegroundColor Red
    exit 2
}
$prevHosts = @([regex]::Matches($listMatch.Groups['list'].Value,
    '"(?<h>andro\.evrenesoglu\d+\.click)"') |
    ForEach-Object { $_.Groups['h'].Value } |
    Select-Object -Unique)

# Onceki birincil hala dogrulanmis makineler arasindaysa korun; yoksa ilk
# (en kucuk numarali) dogrulanmis makine birincil olur.
$verifiedHosts = @($verified | ForEach-Object { Get-Host $_.N })
$primary = if ($prevPrimary -and $prevPrimary -in $verifiedHosts) { $prevPrimary } else { $verifiedHosts[0] }

# Siralama: birincil en basa, sonra numaraya yakin olandan uzağa.
$primaryN = [int]([regex]::Match($primary, "evrenesoglu(\d+)").Groups[1].Value)
$preferred = @($verifiedHosts | Sort-Object { [Math]::Abs([int]([regex]::Match($_, "evrenesoglu(\d+)").Groups[1].Value) - $primaryN) })

# Liste ayni ise tarih degistirme -> bos commit olmasin.
$listChanged = (($preferred -join ",") -ne ($prevHosts -join ","))
$lastScan = if ($listChanged -or -not $prevScan) { (Get-Date).ToString("yyyy-MM-dd") } else { $prevScan }

$hostLines = ($preferred | ForEach-Object { '        "' + $_ + '",' }) -join "`n"
$newBlock = @"
    /** Son taramada dogrulanan birincil makine. */
    const val PRIMARY_HOST = "$primary"

    /** Son taramada 12 kanal yolu da dogrulanmis makineler (yakindan uzağa). */
    val PREFERRED_HOSTS: List<String> = listOf(
$hostLines
    )

    /** Listenin son tarama tarihi (bilgi amaclidir). */
    const val LAST_SCAN = "$lastScan"
"@ + "`n    "

Write-Host ""
Write-Host "Birincil makine : $primary$(if ($prevPrimary -and $prevPrimary -ne $primary) { " (onceki: $prevPrimary)" })"
Write-Host "Listeye alinan  : $($preferred.Count) makine$(if (-not $listChanged) { " (liste degismedi; tarih korundu: $lastScan)" })"
Write-Host ("  " + ($preferred -join " · "))

if ($NoWrite) {
    Write-Host "-NoWrite verildi; dosya degistirilmedi." -ForegroundColor Yellow
} else {
    $updated = [regex]::Replace($raw, $pattern, { $newBlock })
    Set-Content -Path $ProviderFile -Value $updated -NoNewline -Encoding UTF8
    Write-Host "DirectStreams.kt guncellendi." -ForegroundColor Green
}
exit 0
