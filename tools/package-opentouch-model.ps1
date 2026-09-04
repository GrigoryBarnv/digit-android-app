param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath,

    [string]$ConfigPath
)

$model = Get-Item -LiteralPath $ModelPath
if ($model.Extension.ToLowerInvariant() -ne '.onnx') {
    throw 'ModelPath must point to an .onnx file.'
}

if ($ConfigPath) {
    $config = Get-Item -LiteralPath $ConfigPath
    if ($config.Extension.ToLowerInvariant() -ne '.json') {
        throw 'ConfigPath must point to a .json file.'
    }
}

$packagePath = Join-Path $model.DirectoryName ($model.BaseName + '.opentouchmodel')
$temporaryZipPath = Join-Path $env:TEMP ([Guid]::NewGuid().ToString('N') + '.zip')
$staging = Join-Path $env:TEMP ('opentouch-model-' + [Guid]::NewGuid().ToString('N'))

try {
    New-Item -ItemType Directory -Path $staging | Out-Null
    Copy-Item -LiteralPath $model.FullName -Destination (Join-Path $staging 'model.onnx')
    if ($ConfigPath) {
        Copy-Item -LiteralPath $config.FullName -Destination (Join-Path $staging 'model.json')
    }
    # Compress-Archive requires a .zip extension; the final custom extension
    # is applied only after the archive has been created.
    Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $temporaryZipPath -Force
    Move-Item -LiteralPath $temporaryZipPath -Destination $packagePath -Force
    Write-Output "Created $packagePath"
}
finally {
    if (Test-Path -LiteralPath $temporaryZipPath) {
        Remove-Item -LiteralPath $temporaryZipPath -Force
    }
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force
    }
}
