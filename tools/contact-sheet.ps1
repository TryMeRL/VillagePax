# Builds a magnified contact sheet of the generated textures for visual review.
Add-Type -AssemblyName System.Drawing

$tex = "C:\Users\TryMe\Desktop\MODTYPA KLASS\src\main\resources\assets\villagepax\textures"
$files = @(
    "$tex\block\town_hall_side.png",
    "$tex\block\town_hall_top.png",
    "$tex\block\town_hall_bottom.png",
    "$tex\block\marker_workstation.png",
    "$tex\block\marker_bed.png",
    "$tex\block\marker_storage.png",
    "$tex\block\marker_door.png",
    "$tex\block\marker_decor.png",
    "$tex\item\town_hall_blueprint.png"
)

$scale = 8
$cell  = 16 * $scale
$gap   = 10
$cols  = 3
$rows  = 3
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

$outPath = "C:\Users\TryMe\AppData\Local\Temp\claude\C--Users-TryMe-Desktop-MODTYPA-KLASS\418dba03-19e0-4c16-bf0c-4343f4516753\scratchpad\textures-sheet.png"
$sheet.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$sheet.Dispose()
"sheet: $outPath"
