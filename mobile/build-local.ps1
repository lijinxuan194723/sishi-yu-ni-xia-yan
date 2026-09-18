$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'build-local.mjs') @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
