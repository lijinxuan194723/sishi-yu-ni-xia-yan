param([string]$SdkRoot,[string]$SigningDirectory)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(!$SdkRoot){$SdkRoot=Join-Path $projectRoot 'work/android-sdk'}
if(!$SigningDirectory){$SigningDirectory=Join-Path (Split-Path $projectRoot -Parent) '夏彦软件-assets/android-signing'}
$androidJar=(Get-ChildItem -LiteralPath $SdkRoot -Filter android.jar -Recurse | Select-Object -First 1).FullName
$aapt=(Get-ChildItem -LiteralPath $SdkRoot -Filter aapt.exe -Recurse | Select-Object -First 1).FullName
if(!$androidJar -or !$aapt){throw 'Android platform/build-tools are missing'}
$buildTools=Split-Path $aapt -Parent
$output=Join-Path $projectRoot 'outputs/android'
$stage=Join-Path 'F:\LukeBuild' ('luke-apk-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path "$stage/assets","$stage/classes","$stage/dex",$output,$SigningDirectory | Out-Null
Push-Location $projectRoot
try {
 & npx.cmd vite build --config mobile/vite.config.mts
 if($LASTEXITCODE){throw 'Mobile web build failed'}
 Copy-Item -LiteralPath "$projectRoot/work/mobile-web" -Destination "$stage/assets/web" -Recurse
 $personalModel=Join-Path $SigningDirectory 'personal-model.json'
 if(Test-Path -LiteralPath $personalModel){Copy-Item -LiteralPath $personalModel -Destination "$stage/assets/personal-model.json"}
 Copy-Item -LiteralPath "$PSScriptRoot/android" -Destination "$stage/native" -Recurse
 Copy-Item -LiteralPath $androidJar -Destination "$stage/android.jar"
 $androidJar="$stage/android.jar"
 & javac -encoding UTF-8 --release 8 -classpath $androidJar -d "$stage/classes" "$stage/native/MainActivity.java"
 if($LASTEXITCODE){throw 'Java compilation failed'}
 $classes=Get-ChildItem "$stage/classes" -Filter '*.class' -Recurse | ForEach-Object FullName
 & "$buildTools/d8.bat" --lib $androidJar --min-api 26 --output "$stage/dex" @classes
 if($LASTEXITCODE){throw 'DEX compilation failed'}
 & $aapt package -f -M "$stage/native/AndroidManifest.xml" -S "$stage/native/res" -A "$stage/assets" -I $androidJar -F "$stage/unsigned.apk"
 if($LASTEXITCODE){throw 'APK resource packaging failed'}
 Push-Location "$stage/dex"
 try { & $aapt add "$stage/unsigned.apk" classes.dex; if($LASTEXITCODE){throw 'DEX packaging failed'} } finally {Pop-Location}
 & "$buildTools/zipalign.exe" -f -p 4 "$stage/unsigned.apk" "$stage/aligned.apk"
 if($LASTEXITCODE){throw 'Alignment failed'}
 $keyFile=Join-Path $SigningDirectory 'luke-release.p12'
 $passFile=Join-Path $SigningDirectory 'signing-password.txt'
 if(!(Test-Path -LiteralPath $keyFile)){
  if(Test-Path -LiteralPath $passFile){throw 'Signing key is missing; restore it before rebuilding'}
  $bytes=New-Object byte[] 32
  $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
  try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
  [IO.File]::WriteAllText($passFile,[Convert]::ToBase64String($bytes))
  & keytool -genkeypair -alias luke -keystore $keyFile -storetype PKCS12 -storepass:file $passFile -keypass:file $passFile -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Luke Summer Personal App'
  if($LASTEXITCODE){throw 'Signing key creation failed'}
 }
 $version=(Get-Content -Raw -LiteralPath "$projectRoot/package.json" | ConvertFrom-Json).version
 $apkName="Four-Seasons-Luke-$version.apk"
 $apk=Join-Path $stage $apkName
 & "$buildTools/apksigner.bat" sign --ks $keyFile --ks-key-alias luke --ks-pass "file:$passFile" --out $apk "$stage/aligned.apk"
 if($LASTEXITCODE){throw 'APK signing failed'}
 & "$buildTools/apksigner.bat" verify --verbose $apk
 if($LASTEXITCODE){throw 'APK signature verification failed'}
 & "$buildTools/zipalign.exe" -c -v 4 $apk | Select-Object -Last 1
 if($LASTEXITCODE){throw 'APK alignment verification failed'}
 & $aapt dump badging $apk | Select-String 'package:|sdkVersion|targetSdkVersion|application-label:|launchable-activity|uses-permission:'
 if($LASTEXITCODE){throw 'Manifest check failed'}
 Copy-Item -LiteralPath $apk -Destination (Join-Path $output $apkName)
 Get-FileHash -LiteralPath $apk | Format-List
} finally {Pop-Location}




