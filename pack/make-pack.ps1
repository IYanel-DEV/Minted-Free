# ---------------------------------------------------------------------------
# make-pack.ps1 - builds the Minted resource pack.
#
# Writes every model JSON, draws the note and wallet textures with GDI+ and
# zips the result into Minted-ResourcePack.zip (one level above this file).
#
# Design rules:
#   * Banknotes live at custom_model_data 7000 + index into the plugin's
#     descending denomination ladder. Plain paper has NO custom_model_data,
#     so vanilla paper keeps its normal texture - this pack never touches it.
#   * The wallet item is skinned at custom_model_data 7999 (the plugin writes
#     that int into the item NBT itself).
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mcJson   = Join-Path $root 'assets\minecraft\models\item'
$jsonDir  = Join-Path $root 'assets\minted\models\item'
$textures = Join-Path $root 'assets\minted\textures\item'
New-Item -ItemType Directory -Force -Path $mcJson   | Out-Null
New-Item -ItemType Directory -Force -Path $jsonDir  | Out-Null
New-Item -ItemType Directory -Force -Path $textures | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $root 'assets\minecraft\textures\item') | Out-Null

# custom_model_data skidding plan for the default denomination ladder. The
# plugin mints notes with model index = position in the sorted-descending
# ladder [1e9, 1e8, ..., 1], so:
#   7000 -> largest note ($1B) ... 7011 -> smallest ($1)
$notes = @(
  @{ id = 7000; name = 'banknote_0';  label = '1B';    top = @(152, 24, 28);  bottom = @(76, 8, 14) },
  @{ id = 7001; name = 'banknote_1';  label = '100M';  top = @(122, 42, 130); bottom = @(64, 20, 84) },
  @{ id = 7002; name = 'banknote_2';  label = '10M';   top = @(28, 118, 138); bottom = @(10, 58, 82) },
  @{ id = 7003; name = 'banknote_3';  label = '1M';    top = @(40, 138, 64);  bottom = @(18, 72, 34) },
  @{ id = 7004; name = 'banknote_4';  label = '100K';  top = @(72, 118, 42);  bottom = @(34, 62, 22) },
  @{ id = 7005; name = 'banknote_5';  label = '10K';   top = @(168, 128, 34); bottom = @(96, 70, 12) },
  @{ id = 7006; name = 'banknote_6';  label = '1K';    top = @(206, 162, 62); bottom = @(132, 96, 26) },
  @{ id = 7007; name = 'banknote_7';  label = '100';   top = @(220, 142, 34); bottom = @(146, 84, 22) },
  @{ id = 7008; name = 'banknote_8';  label = '50';    top = @(92, 112, 152); bottom = @(42, 58, 90) },
  @{ id = 7009; name = 'banknote_9';  label = '10';    top = @(124, 142, 162); bottom = @(60, 74, 92) },
  @{ id = 7010; name = 'banknote_10'; label = '5';     top = @(110, 82, 50);  bottom = @(62, 44, 26) },
  @{ id = 7011; name = 'banknote_11'; label = '1';     top = @(102, 130, 100); bottom = @(48, 72, 50) }
)

# ---------------------------------------------------------------------------
# Texture drawing
# ---------------------------------------------------------------------------

function New-NoteTexture {
  param($path, $label, [int[]]$top, [int[]]$bottom)

  $w = 32; $h = 32
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

  $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
  $shade = New-Object System.Drawing.Color
  $shade = [System.Drawing.Color]::Empty
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
      [System.Drawing.Color]::FromArgb(255, $top[0], $top[1], $top[2]),
      [System.Drawing.Color]::FromArgb(255, $bottom[0], $bottom[1], $bottom[2]),
      [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
  $g.FillRectangle($grad, $rect)

  $edge = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 18, 16, 12), 2)
  $g.DrawRectangle($edge, 1, 1, $w - 3, $h - 3)

  $veil = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(130, 0, 0, 0))
  $g.FillRectangle($veil, 4, 4, $w - 8, $h - 8)

  $gold = [System.Drawing.Color]::FromArgb(255, 236, 208, 92)
  $goldBrush = New-Object System.Drawing.SolidBrush($gold)
  $g.FillEllipse($goldBrush, 5, 5, 9, 9)
  $round = [System.Drawing.StringFormat]::new()
  $round.Alignment = [System.Drawing.StringAlignment]::Center
  $round.LineAlignment = [System.Drawing.StringAlignment]::Center
  $emblemFont = New-Object System.Drawing.Font('Arial Black', 7, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $dark = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 60, 40, 8))
  $emblemRect = New-Object System.Drawing.RectangleF(5, 4, 9, 10)
  $g.DrawString('M', $emblemFont, $dark, $emblemRect, $round)

  $g.DrawString('MINTED', (New-Object System.Drawing.Font('Arial', 4, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)),
      $goldBrush, (New-Object System.Drawing.RectangleF(15, 2, 16, 6)), $round)

  $size = if ($label.Length -le 2) { 13 } elseif ($label.Length -eq 3) { 11 } else { 9 }
  $labelFont = New-Object System.Drawing.Font('Arial Black', $size, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $numberRect = New-Object System.Drawing.RectangleF(0, 13, 32, 16)
  $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
  $outline = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 10, 10, 10))
  foreach ($off in @('0,-1', '0,1', '-1,0', '1,0')) {
    $p = $off.Split(',')
    $shifted = New-Object System.Drawing.RectangleF(($numberRect.X + [int]$p[0]), ($numberRect.Y + [int]$p[1]), $numberRect.Width, $numberRect.Height)
    $g.DrawString($label, $labelFont, $outline, $shifted, $round)
  }
  $g.DrawString($label, $labelFont, $white, $numberRect, $round)

  $g.Dispose(); $grad.Dispose(); $edge.Dispose(); $veil.Dispose(); $goldBrush.Dispose()
  $dark.Dispose(); $white.Dispose(); $outline.Dispose(); $labelFont.Dispose(); $emblemFont.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function New-WalletTexture {
  param($path)

  $w = 32; $h = 32
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

  $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
      [System.Drawing.Color]::FromArgb(255, 122, 88, 62),
      [System.Drawing.Color]::FromArgb(255, 66, 44, 30),
      [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
  $g.FillRectangle($grad, $rect)

  $edge = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 34, 22, 14), 2)
  $g.DrawRectangle($edge, 1, 1, $w - 3, $h - 3)

  # pocket flap seam
  $seam = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 44, 28, 18), 2)
  $g.DrawLine($seam, 3, 12, 28, 12)

  # gold clasp
  $gold = [System.Drawing.Color]::FromArgb(255, 238, 204, 92)
  $goldBrush = New-Object System.Drawing.SolidBrush($gold)
  $g.FillRectangle($goldBrush, 13, 13, 6, 6)

  $round = [System.Drawing.StringFormat]::new()
  $round.Alignment = [System.Drawing.StringAlignment]::Center
  $round.LineAlignment = [System.Drawing.StringAlignment]::Center
  $money = New-Object System.Drawing.Font('Arial Black', 12, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $g.DrawString('$', $money, $goldBrush, (New-Object System.Drawing.RectangleF(0, 3, 32, 16)), $round)

  $g.Dispose(); $grad.Dispose(); $edge.Dispose(); $seam.Dispose(); $goldBrush.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

foreach ($n in $notes) {
  New-NoteTexture -path (Join-Path $textures ($n.name + '.png')) -label $n.label -top $n.top -bottom $n.bottom
}
New-WalletTexture -path (Join-Path $textures 'wallet.png')

# ---------------------------------------------------------------------------
# Model JSON (written without BOM so every client parses them cleanly)
# ---------------------------------------------------------------------------

function Write-JsonFile {
  param($path, $text)
  [System.IO.File]::WriteAllText((Join-Path $root $path), $text, (New-Object System.Text.UTF8Encoding($false)))
}

Write-JsonFile 'pack.mcmeta' '{"pack":{"pack_format":4,"description":"Minted: skinned banknotes, checks and wallets. Vanilla paper is untouched."}}'

$paper = @()
$paper += '{'
$paper += '  "parent": "minecraft:item/generated",'
$paper += '  "textures": { "layer0": "minecraft:item/paper" },'
$paper += '  "overrides": ['
for ($i = 0; $i -lt $notes.Count; $i++) {
  $comma = if ($i -lt $notes.Count - 1) { ',' } else { '' }
  $paper += '    { "predicate": { "custom_model_data": ' + $notes[$i].id + ' }, "model": "minted:item/' + $notes[$i].name + '" }' + $comma
}
$paper += '  ]'
$paper += '}'
Write-JsonFile 'assets\minecraft\models\item\paper.json' ($paper -join "`n")

$leather = @()
$leather += '{'
$leather += '  "parent": "minecraft:item/generated",'
$leather += '  "textures": { "layer0": "minecraft:item/leather" },'
$leather += '  "overrides": ['
$leather += '    { "predicate": { "custom_model_data": 7999 }, "model": "minted:item/wallet" }'
$leather += '  ]'
$leather += '}'
Write-JsonFile 'assets\minecraft\models\item\leather.json' ($leather -join "`n")

foreach ($n in $notes) {
  $model = '{ "parent": "minecraft:item/generated", "textures": { "layer0": "minted:item/' + $n.name + '" } }'
  Write-JsonFile ('assets\minted\models\item\' + $n.name + '.json') $model
}
Write-JsonFile 'assets\minted\models\item\wallet.json' '{ "parent": "minecraft:item/generated", "textures": { "layer0": "minted:item/wallet" } }'

# ---------------------------------------------------------------------------
# Zip (pack root only - the script itself stays out of the archive)
# ---------------------------------------------------------------------------

$zip = Join-Path (Split-Path -Parent $root) 'Minted-ResourcePack.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Push-Location $root
Compress-Archive -Path 'pack.mcmeta', 'assets' -DestinationPath $zip -CompressionLevel Optimal
Pop-Location
Write-Host "Wrote $zip"