# Compatibility entry. Prefer build-local.ps1; never generate a replacement key or bundle private-model.json.
$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'build-local.mjs') @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
