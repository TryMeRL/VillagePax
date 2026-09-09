# Draws the mod icon: a Norman house, 32x32 pixels scaled to 128x128.
#
# Scaled with nearest neighbour on purpose: the icon must look like the
# blocks it advertises, and a smoothed one would look like a different mod.
# ASCII only: Windows PowerShell 5.1 reads UTF-8 files without BOM as ANSI.
Add-Type -AssemblyName System.Drawing

$out = "C:\Users\TryMe\Desktop\MODTYPA KLASS\src\main\resources\assets\villagepax\icon.png"

# Same palette as the block textures: the icon has to belong to them.
$hex = New-Object "System.Collections.Generic.Dictionary[string,string]" ([System.StringComparer]::Ordinal)
$hex.Add(" ","00000000")   # transparent
$hex.Add("s","FF6E8FA8")   # sky
$hex.Add("g","FF5A7A3A")   # grass
$hex.Add("G","FF6E8F46")   # grass, lit
$hex.Add("#","FF4A3524")   # timber beam
$hex.Add("=","FF5E4531")   # beam, lit
$hex.Add(".","FFD9CDB4")   # plaster
$hex.Add(",","FFC7BAA0")   # plaster, shaded
$hex.Add("'","FFE6DCC6")   # plaster, lit
$hex.Add("R","FF3A281B")   # roof, dark
$hex.Add("r","FF4E3724")   # roof
$hex.Add("d","FF6E5330")   # door
$hex.Add("y","FFE0A030")   # window light
$hex.Add("S","FF8C8C87")   # stone
$hex.Add("m","FFA8A8A2")   # mortar

$color = New-Object "System.Collections.Generic.Dictionary[string,System.Drawing.Color]" ([System.StringComparer]::Ordinal)
foreach ($k in $hex.Keys) {
    $v = $hex[$k]
    $color.Add($k, [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($v.Substring(0,2),16),
        [Convert]::ToInt32($v.Substring(2,2),16),
        [Convert]::ToInt32($v.Substring(4,2),16),
        [Convert]::ToInt32($v.Substring(6,2),16)))
}

$map = @(
    "ssssssssssssssssssssssssssssssss"
    "ssssssssssssssssssssssssssssssss"
    "sssssssssssssssRRsssssssssssssss"
    "ssssssssssssssRrrRssssssssssssss"
    "sssssssssssssRrrrrRsssssssssssss"
    "ssssssssssssRrrrrrrRssssssssssss"
    "sssssssssssRrrrrrrrrRsssssssssss"
    "ssssssssssRrrrrrrrrrrRssssssssss"
    "sssssssssRrrrrrrrrrrrrRsssssssss"
    "ssssssssRrrrrrrrrrrrrrrRssssssss"
    "sssssssRrrrrrrrrrrrrrrrrRsssssss"
    "ssssssRrrrrrrrrrrrrrrrrrrRssssss"
    "sssssRRRRRRRRRRRRRRRRRRRRRRsssss"
    "ssssss#'''''#''''''#'''''#ssssss"
    "ssssss#...,.#..yy..#,....#ssssss"
    "ssssss#..,..#.,yy..#....,#ssssss"
    "ssssss#.,...#,....,#...,.#ssssss"
    "ssssss####################ssssss"
    "ssssss#,....#.,....#.,...#ssssss"
    "ssssss#.....#,.dd..#,....#ssssss"
    "ssssss#.....#..dd..#.....#ssssss"
    "ssssss#....,#..dd.,#.....#ssssss"
    "ssssss#...,.#..dd,.#....,#ssssss"
    "sssssSmSSmSSmSSmSSmSSmSSmSSsssss"
    "sssssmmmSmmmSmmmSmmmSmmmSmmsssss"
    "GggggggGggggggGggggggGgggGgggggg"
    "gGggggggGggggggGggggggGgggGggggg"
    "ggGggggggGggggggGggggggGgggGgggg"
    "gggGggggggGggggggGggggggGgggGggg"
    "ggggGggggggGggggggGggggggGgggGgg"
    "gggggGggggggGggggggGggggggGgggGg"
    "ggggggGggggggGggggggGggggggGgggG"
)

if ($map.Count -ne 32) { throw "map has $($map.Count) rows, expected 32" }

$small = New-Object System.Drawing.Bitmap 32, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 32; $y++) {
    $row = $map[$y]
    if ($row.Length -ne 32) { throw "row ${y} has $($row.Length) chars, expected 32" }
    for ($x = 0; $x -lt 32; $x++) {
        $ch = $row.Substring($x, 1)
        if (-not $color.ContainsKey($ch)) { throw "no color for [$ch] at ${x},${y}" }
        $small.SetPixel($x, $y, $color[$ch])
    }
}

$icon = New-Object System.Drawing.Bitmap 128, 128, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($icon)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage($small, 0, 0, 128, 128)
$g.Dispose()

$icon.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Dispose()
$small.Dispose()
"icon: $out"
