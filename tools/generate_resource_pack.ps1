param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$packRoot = Join-Path $Root 'resource-pack\VibePetCore'
$distDir = Join-Path $Root 'build\resource-pack'
$zipPath = Join-Path $distDir 'VibePetCore-resource-pack.zip'
$bundledZipPath = Join-Path $Root 'src\main\resources\resource-pack\VibePetCore-resource-pack.zip'

$pets = @(
    @{ key='axolotl'; material='axolotl_spawn_egg'; egg=200101; empty=200201; color='#e9899a' },
    @{ key='bat'; material='bat_spawn_egg'; egg=200102; empty=200202; color='#4b3d4f' },
    @{ key='bee'; material='bee_spawn_egg'; egg=200103; empty=200203; color='#f3c743' },
    @{ key='blaze'; material='blaze_spawn_egg'; egg=200104; empty=200204; color='#f4a12d' },
    @{ key='breeze'; material='breeze_spawn_egg'; egg=200105; empty=200205; color='#9bd4d5' },
    @{ key='cat'; material='cat_spawn_egg'; egg=200106; empty=200206; color='#d59a5b' },
    @{ key='fox'; material='fox_spawn_egg'; egg=200107; empty=200207; color='#d8782d' },
    @{ key='frog'; material='frog_spawn_egg'; egg=200108; empty=200208; color='#6aa84f' },
    @{ key='ghast'; material='ghast_spawn_egg'; egg=200109; empty=200209; color='#e8e2d7' },
    @{ key='panda'; material='panda_spawn_egg'; egg=200110; empty=200210; color='#f0eee7' },
    @{ key='parrot'; material='parrot_spawn_egg'; egg=200111; empty=200211; color='#d73535' },
    @{ key='phantom'; material='phantom_spawn_egg'; egg=200112; empty=200212; color='#43506c' },
    @{ key='rabbit'; material='rabbit_spawn_egg'; egg=200113; empty=200213; color='#c7b08d' },
    @{ key='allay'; material='allay_spawn_egg'; egg=200114; empty=200214; color='#67d6ef' },
    @{ key='armadillo'; material='armadillo_spawn_egg'; egg=200115; empty=200215; color='#a06f55' },
    @{ key='vex'; material='vex_spawn_egg'; egg=200116; empty=200216; color='#c7d7e8' },
    @{ key='wolf'; material='wolf_spawn_egg'; egg=200117; empty=200217; color='#d8d8d8' }
)

function New-Utf8File([string]$Path, [string]$Content) {
    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Hex-ToColor([string]$Hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($Hex)
}

function Save-PetTexture([string]$Path, [string]$HexColor, [bool]$Empty) {
    Add-Type -AssemblyName System.Drawing
    $bitmap = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gfx = [System.Drawing.Graphics]::FromImage($bitmap)
    $gfx.Clear([System.Drawing.Color]::Transparent)
    $gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

    $glass = [System.Drawing.Color]::FromArgb(108, 190, 238, 255)
    $glassEdge = [System.Drawing.Color]::FromArgb(178, 112, 174, 218)
    $shine = [System.Drawing.Color]::FromArgb(170, 255, 255, 255)
    $core = Hex-ToColor $HexColor
    if ($Empty) {
        $core = [System.Drawing.Color]::FromArgb(100, $core.R, $core.G, $core.B)
    }
    $shadow = [System.Drawing.Color]::FromArgb(180, [Math]::Max(0, $core.R - 70), [Math]::Max(0, $core.G - 70), [Math]::Max(0, $core.B - 70))

    $gfx.FillEllipse([System.Drawing.SolidBrush]::new($glass), 2, 1, 12, 14)
    $gfx.DrawEllipse([System.Drawing.Pen]::new($glassEdge, 1), 2, 1, 12, 14)
    $gfx.FillRectangle([System.Drawing.SolidBrush]::new($shine), 5, 3, 2, 2)

    if ($Empty) {
        $gfx.DrawEllipse([System.Drawing.Pen]::new($shadow, 2), 5, 6, 6, 5)
        $gfx.DrawLine([System.Drawing.Pen]::new($shadow, 1), 6, 11, 10, 11)
    } else {
        $gfx.FillEllipse([System.Drawing.SolidBrush]::new($shadow), 4, 7, 8, 5)
        $gfx.FillEllipse([System.Drawing.SolidBrush]::new($core), 4, 5, 8, 7)
        $gfx.FillRectangle([System.Drawing.SolidBrush]::new($core), 6, 3, 4, 3)
        $gfx.FillRectangle([System.Drawing.SolidBrush]::new($shadow), 5, 10, 2, 2)
        $gfx.FillRectangle([System.Drawing.SolidBrush]::new($shadow), 9, 10, 2, 2)
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $gfx.Dispose()
    $bitmap.Dispose()
}

Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $packRoot
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $distDir
New-Item -ItemType Directory -Force -Path $packRoot | Out-Null
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

New-Utf8File (Join-Path $packRoot 'pack.mcmeta') @'
{
  "pack": {
    "pack_format": 75,
    "description": "VibePetCore pet egg miniatures"
  }
}
'@

foreach ($pet in $pets) {
    $itemDefinition = @"
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/$($pet.material)"
    },
    "entries": [
      {
        "threshold": $($pet.egg),
        "model": {
          "type": "minecraft:model",
          "model": "vibepetcore:item/egg/$($pet.key)"
        }
      },
      {
        "threshold": $($pet.empty),
        "model": {
          "type": "minecraft:model",
          "model": "vibepetcore:item/empty/$($pet.key)"
        }
      }
    ]
  }
}
"@
    New-Utf8File (Join-Path $packRoot "assets\minecraft\items\$($pet.material).json") $itemDefinition

    $eggModel = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "vibepetcore:item/egg/$($pet.key)"
  }
}
"@
    New-Utf8File (Join-Path $packRoot "assets\vibepetcore\models\item\egg\$($pet.key).json") $eggModel

    $emptyModel = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "vibepetcore:item/empty/$($pet.key)"
  }
}
"@
    New-Utf8File (Join-Path $packRoot "assets\vibepetcore\models\item\empty\$($pet.key).json") $emptyModel

    Save-PetTexture (Join-Path $packRoot "assets\vibepetcore\textures\item\egg\$($pet.key).png") $pet.color $false
    Save-PetTexture (Join-Path $packRoot "assets\vibepetcore\textures\item\empty\$($pet.key).png") $pet.color $true
}

Compress-Archive -Path (Join-Path $packRoot '*') -DestinationPath $zipPath -Force
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $bundledZipPath) | Out-Null
Copy-Item -LiteralPath $zipPath -Destination $bundledZipPath -Force
Write-Host "Resource pack generated: $zipPath"
Write-Host "Bundled resource updated: $bundledZipPath"
