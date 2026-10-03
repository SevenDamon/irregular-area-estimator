$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildRoot = Join-Path $PSScriptRoot 'build'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$javaRoot = 'D:\Program Files\Android\Android Studio\jbr'
$toolsRoot = Join-Path $sdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-35\android.jar'
$javaBin = Join-Path $javaRoot 'bin'
$aapt2 = Join-Path $toolsRoot 'aapt2.exe'
$d8 = Join-Path $toolsRoot 'd8.bat'
$zipalign = Join-Path $toolsRoot 'zipalign.exe'
$apksigner = Join-Path $toolsRoot 'apksigner.bat'
$stage = Join-Path $buildRoot 'stage'
$classes = Join-Path $buildRoot 'classes'
$dex = Join-Path $buildRoot 'dex'
$res = Join-Path $buildRoot 'res'
$compiled = Join-Path $buildRoot 'compiled'
$unsigned = Join-Path $buildRoot 'unsigned.apk'
$aligned = Join-Path $buildRoot 'aligned.apk'
$keystore = Join-Path $buildRoot 'trial-debug.keystore'
$apk = Join-Path $PSScriptRoot 'area-estimator-offline-trial.apk'

foreach ($path in @($javaBin, $aapt2, $d8, $zipalign, $apksigner, $androidJar)) {
  if (-not (Test-Path $path)) { throw "Missing build dependency: $path" }
}
$buildFull = [IO.Path]::GetFullPath($buildRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
foreach ($target in @($stage, $classes, $dex, $res, $compiled)) {
  $targetFull = [IO.Path]::GetFullPath($target)
  if (-not $targetFull.StartsWith($buildFull + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clear a path outside the build directory: $targetFull"
  }
  if (Test-Path -LiteralPath $targetFull) { Remove-Item -LiteralPath $targetFull -Recurse -Force }
}
New-Item -ItemType Directory -Force $stage, $classes, $dex, (Join-Path $res 'drawable-nodpi'), $compiled | Out-Null
$assets = Join-Path $stage 'assets'
New-Item -ItemType Directory -Force $assets | Out-Null
foreach ($name in @('vision_bundle.mjs','hand_landmarker.task','magic_touch.tflite','manifest.webmanifest','icon-192.png','icon-512.png')) {
  Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination (Join-Path $assets $name) -Force
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'wasm') -Destination (Join-Path $assets 'wasm') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'icon-192.png') -Destination (Join-Path $res 'drawable-nodpi\app_icon.png') -Force

# The APK already contains every model file; its WebView does not need a service worker.
$html = [IO.File]::ReadAllText((Join-Path $projectRoot 'index.html'))
$html = $html.Replace('v12.6 · 2026-10-04 · 自动识别试验版', 'v12.6 · 2026-10-04 · 安卓离线试验版')
$old = "if('serviceWorker' in navigator && (location.protocol==='https:' || location.hostname==='localhost' || location.hostname==='127.0.0.1')){"
$new = "if('serviceWorker' in navigator && location.hostname!=='appassets.androidplatform.net' && (location.protocol==='https:' || location.hostname==='localhost' || location.hostname==='127.0.0.1')){"
if (-not $html.Contains($old)) { throw 'Could not find service worker guard in index.html' }
[IO.File]::WriteAllText((Join-Path $assets 'index.html'), $html.Replace($old,$new), [Text.UTF8Encoding]::new($false))

& (Join-Path $javaBin 'javac.exe') -encoding UTF-8 -source 8 -target 8 -cp $androidJar -d $classes (Join-Path $PSScriptRoot 'src\cn\codex\irregulararea\MainActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& (Join-Path $javaBin 'jar.exe') cf (Join-Path $buildRoot 'classes.jar') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
$env:JAVA_HOME = $javaRoot
& $d8 --lib $androidJar --min-api 29 --output $dex (Join-Path $buildRoot 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& $aapt2 compile -o $compiled (Join-Path $res 'drawable-nodpi\app_icon.png')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 resource compile failed' }
$flat = Get-ChildItem -LiteralPath $compiled -Filter '*.flat' | Select-Object -First 1
if (-not $flat) { throw 'Compiled icon resource missing' }
& $aapt2 link -o $unsigned --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -I $androidJar --min-sdk-version 29 --target-sdk-version 35 -R $flat.FullName
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
& (Join-Path $javaBin 'jar.exe') uf $unsigned -C $stage assets -C $dex classes.dex
if ($LASTEXITCODE -ne 0) { throw 'apk archive update failed' }
& $zipalign -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
if (-not (Test-Path $keystore)) {
  & (Join-Path $javaBin 'keytool.exe') -genkeypair -keystore $keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
  if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& $apksigner sign --ks $keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& $apksigner verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Write-Output "APK: $apk"
