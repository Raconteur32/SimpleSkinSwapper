<#
.SYNOPSIS
    Simule la creation des textureId de SimpleSkinSwapper, comme si le jeu etait lance.

.DESCRIPTION
    Reproduit exactement la logique de SkinEntry.ensureTextureLoaded() :
      - scan de <GameDir>\skins\*.png
      - sanitized = nom du fichier en minuscules, [^a-z0-9_/.-] remplace par '_'
      - pathHash = "%08x" du hashCode Java du chemin absolu, masque 0x7FFFFFFF
      - cle = "skin/entry_<sanitized>_<pathHash>"
      - textureId = "simpleskinswapper:<cle>"
    Signale les collisions de textureId entre fichiers differents.

.PARAMETER GameDir
    Dossier .minecraft utilise par le jeu (celui que FabricLoader voit comme gameDir).
    Defaut : %APPDATA%\.minecraft

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\Compute-TextureIds.ps1
    powershell -ExecutionPolicy Bypass -File tools\Compute-TextureIds.ps1 -GameDir "D:\Minecraft\instances\monprofil"
#>

param(
    [string]$GameDir = "$env:APPDATA\.minecraft"
)

$ErrorActionPreference = 'Stop'

# Reproduction exacte de String.hashCode() Java (arithmetique int32 avec overflow).
function Get-JavaStringHashCode([string]$s) {
    [long]$h = 0
    foreach ($ch in $s.ToCharArray()) {
        $h = ($h * 31 + [int][char]$ch) -band 0xFFFFFFFF
    }
    if ($h -ge 0x80000000) { $h -= 0x100000000 }  # repli en int32 signe
    return [int]$h
}

$skinsDir = Join-Path $GameDir 'skins'
if (-not (Test-Path $skinsDir)) {
    Write-Error "Dossier skins introuvable : $skinsDir"
    exit 1
}

$files = Get-ChildItem -Path $skinsDir -File | Where-Object { $_.Name.ToLowerInvariant().EndsWith('.png') }
if ($files.Count -eq 0) {
    Write-Host "Aucun .png dans $skinsDir"
    exit 0
}

Write-Host "GameDir : $GameDir"
Write-Host ""

$byTextureId = @{}

foreach ($file in $files) {
    # Chemin absolu tel que Java le verrait sous Windows (separateurs '\').
    $absolutePath = $file.FullName

    # SkinEntry.kt:44-48
    $sanitized = $file.Name.ToLowerInvariant() -replace '[^a-z0-9_/.-]', '_'
    $hash = Get-JavaStringHashCode $absolutePath
    $pathHash = '{0:x8}' -f ($hash -band 0x7FFFFFFF)
    $key = "skin/entry_${sanitized}_$pathHash"
    $textureId = "simpleskinswapper:$key"

    [PSCustomObject]@{
        File       = $file.Name
        TextureId  = $textureId
    }

    if (-not $byTextureId.ContainsKey($textureId)) { $byTextureId[$textureId] = @() }
    $byTextureId[$textureId] += $file.Name
}

# Detection de collisions : meme textureId pour des fichiers differents.
$collisions = $byTextureId.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 }
if ($collisions) {
    Write-Host ""
    Write-Warning "Collisions de textureId detectees :"
    foreach ($c in $collisions) {
        Write-Warning ("  {0}  <-  {1}" -f $c.Key, ($c.Value -join ', '))
    }
} else {
    Write-Host ""
    Write-Host "Aucune collision de textureId ($($files.Count) fichier(s))."
}
