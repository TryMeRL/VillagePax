# Builds a magnified contact sheet of the generated textures for visual review.
#
# Takes every texture under assets/villagepax/textures, so a newly drawn block
# shows up without editing this file. Pass -Out to choose where the sheet goes.
param(
    [string]$Out = ""
)

Add-Type -AssemblyName System.Drawing

$tex = "C:\Users\TryMe\Desktop\MODTYPA KLASS\src\main\resources\assets\villagepax\textures"

# Sorted so the sheet is stable between runs and easy to compare by eye.
$files = @(Get-ChildItem -Path "$tex\block", "$tex\item" -Filter *.png -ErrorAction SilentlyContinue |
    Sort-Object FullName | ForEach-Object { $_.FullName })

if ($files.Count -eq 0) { throw "no textures found under $tex" }

if ($Out -eq "") {
    $Out = Join-Path ([System.IO.Path]::GetTempPath()) "villagepax-textures-sheet.png"
}

$scale = 8
$cell  = 16 * $scale
$gap   = 10
$cols  = [math]::Min(4, $files.Count)
$rows  = [math]::Ceiling($files.Count / $cols)
$w = $cols * $cell + ($cols + 1) * $gap
$h = $rows * $cell + ($rows + 1) * $gap + 22 * $rows

$sheet = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 40, 44, 48))

$font  = New-Object System.Drawing.Font("Consolas", 9)
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 200, 210, 205))

for ($i = 0; $i -lt $files.Count; $i++) {
    $col = $i % $cols
    $row = [math]::Floor($i / $cols)
    $x = $gap + $col * ($cell + $gap)
    $y = $gap + $row * ($cell + $gap + 22)

    $img = [System.Drawing.Image]::FromFile($files[$i])
    $g.DrawImage($img, $x, $y, $cell, $cell)
    $img.Dispose()

    $label = [System.IO.Path]::GetFileNameWithoutExtension($files[$i])
    $g.DrawString($label, $font, $brush, $x, $y + $cell + 3)
}

$sheet.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$sheet.Dispose()
"sheet: $Out  ($($files.Count) textures)"
