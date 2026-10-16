param(
  [Parameter(Mandatory=$true)][ValidateSet("knocklauncher","quickshot")][string]$App,
  [string]$Sdk = $env:ANDROID_HOME
)

$ErrorActionPreference = "Stop"
if (-not $Sdk -or -not (Test-Path $Sdk)) {
  $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if (-not (Test-Path $Sdk)) { throw "找不到 Android SDK，请设置 ANDROID_HOME" }

$btRoot = Join-Path $Sdk "build-tools"
$bt = (Get-ChildItem $btRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platforms = @("android-36","android-35","android-34")
$aj = $null
foreach ($p in $platforms) {
  $cand = Join-Path $Sdk "platforms\$p\android.jar"
  if (Test-Path $cand) { $aj = $cand; break }
}
if (-not $aj) { throw "找不到 android.jar（需要 platforms/android-34 或更新）" }

$root = $PSScriptRoot
$proj = Join-Path $root $App
if (-not (Test-Path $proj)) { throw "找不到工程目录 $proj" }
Set-Location $proj

Write-Host "SDK      : $Sdk"
Write-Host "build-tools: $bt"
Write-Host "android.jar: $aj"

# 1) 资源 + manifest
& "$bt\aapt2.exe" link -o base.apk -I "$aj" --manifest AndroidManifest.xml

# 2) 编译（必须 UTF-8！）
Remove-Item -Recurse -Force classes -ErrorAction SilentlyContinue
$src = Get-ChildItem -Recurse "src" -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "$aj" -d classes $src

# 3) dex
Remove-Item -Recurse -Force dexout -ErrorAction SilentlyContinue
New-Item -ItemType Directory dexout | Out-Null
$cf = Get-ChildItem -Recurse classes -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --lib "$aj" --min-api 26 --output dexout $cf

# 4) 打包
Remove-Item unsigned.apk, aligned.apk, "$App.apk" -ErrorAction SilentlyContinue
Copy-Item base.apk unsigned.apk
& jar uf unsigned.apk -C dexout classes.dex
& "$bt\zipalign.exe" -f -p 4 unsigned.apk aligned.apk

# 5) 签名（首次自动生成调试签名）
$ks = Join-Path $proj "debug.keystore"
$pw = Join-Path $proj "kspass.txt"
if (-not (Test-Path $ks)) {
  & keytool -genkeypair -keystore $ks -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}
if (-not (Test-Path $pw)) { "android" | Out-File -Encoding ascii $pw }

& "$bt\apksigner.bat" sign --ks $ks --ks-pass "file:$pw" --out "$App.apk" aligned.apk
& "$bt\apksigner.bat" verify "$App.apk"

Write-Host ""
Write-Host "OK -> $proj\$App.apk" -ForegroundColor Green
Write-Host "install: adb install -r `"$proj\\$App.apk`""
