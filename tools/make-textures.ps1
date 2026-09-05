# Generates 16x16 Minecraft textures for Village Pax from explicit pixel maps.
# ASCII only: Windows PowerShell 5.1 reads UTF-8 files without BOM as ANSI.
Add-Type -AssemblyName System.Drawing

$out = "C:\Users\TryMe\Desktop\MODTYPA KLASS\src\main\resources\assets\villagepax\textures"
New-Item -ItemType Directory -Force -Path "$out\block" | Out-Null
New-Item -ItemType Directory -Force -Path "$out\item"  | Out-Null

# Case-sensitive on purpose: PowerShell hashtables are not, and S/s would collide.
$hex = New-Object "System.Collections.Generic.Dictionary[string,string]" ([System.StringComparer]::Ordinal)
$hex.Add("#","FF4A3524")   # dark timber beam
$hex.Add(".","FFD9CDB4")   # plaster
$hex.Add(",","FFC7BAA0")   # plaster, shaded
$hex.Add("L","FFB08A50")   # plank, light
$hex.Add("M","FF9C7742")   # plank, mid
$hex.Add("d","FF6E5330")   # plank seam
$hex.Add("S","FF8C8C87")   # stone
$hex.Add("s","FF6F6F6B")   # stone, shaded
$hex.Add("m","FFA8A8A2")   # mortar
$hex.Add("K","FF2B2F33")   # marker body
$hex.Add("k","FF21252A")   # marker frame
$hex.Add("A","FFE0A030")   # amber
$hex.Add("R","FFC8443C")   # red
$hex.Add("G","FFC8912E")   # gold
$hex.Add("T","FF2E9E96")   # teal
$hex.Add("P","FF8A5BB0")   # purple
$hex.Add("p","FFE8DCC0")   # parchment
$hex.Add("q","FFD2C4A2")   # parchment, shaded
$hex.Add("i","FF3A4A6B")   # ink
$hex.Add("w","FFB03A32")   # wax seal
$hex.Add(" ","00000000")   # transparent

$color = New-Object "System.Collections.Generic.Dictionary[string,System.Drawing.Color]" ([System.StringComparer]::Ordinal)
foreach ($k in $hex.Keys) {
    $v = $hex[$k]
    $color.Add($k, [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($v.Substring(0,2),16),
        [Convert]::ToInt32($v.Substring(2,2),16),
        [Convert]::ToInt32($v.Substring(4,2),16),
        [Convert]::ToInt32($v.Substring(6,2),16)))
}

# Subtle deterministic noise so flat fills do not look printed.
$noise = New-Object "System.Collections.Generic.Dictionary[string,string]" ([System.StringComparer]::Ordinal)
$noise.Add(".", ",")
$noise.Add("L", "M")
$noise.Add("M", "L")
$noise.Add("S", "s")

$maps = [ordered]@{}

$maps["block\town_hall_side"] = @(
    "################"
    "#..............#"
    "#.##........##.#"
    "#..##......##..#"
    "#...##....##...#"
    "#....##..##....#"
    "#.....####.....#"
    "#......##......#"
    "#......##......#"
    "#.....####.....#"
    "#....##..##....#"
    "#...##....##...#"
    "#..##......##..#"
    "#.##........##.#"
    "#..............#"
    "################")

$maps["block\town_hall_top"] = @(
    "LLLLLLLLLLLLLLLL"
    "LLLLLLLLLLLLLLLL"
    "LLLLLLLLLLLLLLLL"
    "dddddddddddddddd"
    "MMMMMMMMMMMMMMMM"
    "MMMMMMMMMMMMMMMM"
    "MMMMMMMMMMMMMMMM"
    "dddddddddddddddd"
    "LLLLLLLLLLLLLLLL"
    "LLLLLLLLLLLLLLLL"
    "LLLLLLLLLLLLLLLL"
    "dddddddddddddddd"
    "MMMMMMMMMMMMMMMM"
    "MMMMMMMMMMMMMMMM"
    "MMMMMMMMMMMMMMMM"
    "dddddddddddddddd")

$maps["block\town_hall_bottom"] = @(
    "SSSSSSSmSSSSSSSm"
    "SSSSSSSmSSSSSSSm"
    "SSSSSSSmSSSSSSSm"
    "mmmmmmmmmmmmmmmm"
    "SSSmSSSSSSSmSSSS"
    "SSSmSSSSSSSmSSSS"
    "SSSmSSSSSSSmSSSS"
    "mmmmmmmmmmmmmmmm"
    "SSSSSSSmSSSSSSSm"
    "SSSSSSSmSSSSSSSm"
    "SSSSSSSmSSSSSSSm"
    "mmmmmmmmmmmmmmmm"
    "SSSmSSSSSSSmSSSS"
    "SSSmSSSSSSSmSSSS"
    "SSSmSSSSSSSmSSSS"
    "mmmmmmmmmmmmmmmm")

$maps["block\marker_workstation"] = @(
    "kkkkkkkkkkkkkkkk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKAAAAAAAAKKKk"
    "kKKKAAAAAAAAKKKk"
    "kKKKKAAAAAAKKKKk"
    "kKKKKKAAAAKKKKKk"
    "kKKKKKKAAKKKKKKk"
    "kKKKKKKAAKKKKKKk"
    "kKKKKKAAAAKKKKKk"
    "kKKKAAAAAAAAKKKk"
    "kKKAAAAAAAAAAKKk"
    "kKKAAAAAAAAAAKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kkkkkkkkkkkkkkkk")

$maps["block\marker_bed"] = @(
    "kkkkkkkkkkkkkkkk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKRRRRRRRRRRRRKk"
    "kKRRRRRRRRRRRRKk"
    "kKRRRRRRRRRRRRKk"
    "kKRRRRRRRRRRRRKk"
    "kKRRRRRRRRRRRRKk"
    "kKRKKKKKKKKKKRKk"
    "kKRKKKKKKKKKKRKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kkkkkkkkkkkkkkkk")

$maps["block\marker_storage"] = @(
    "kkkkkkkkkkkkkkkk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKGGGGGKGGGGKKk"
    "kKKGGGGGKGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKGGGGGGGGGGKKk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kkkkkkkkkkkkkkkk")

$maps["block\marker_door"] = @(
    "kkkkkkkkkkkkkkkk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKTTTTTTKKKKk"
    "kKKKTTTTTTTTKKKk"
    "kKKTTTKKKKTTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKTTKKKKKKTTKKk"
    "kKKKKKKKKKKKKKKk"
    "kkkkkkkkkkkkkkkk")

$maps["block\marker_decor"] = @(
    "kkkkkkkkkkkkkkkk"
    "kKKKKKKKKKKKKKKk"
    "kKKKKKKPPKKKKKKk"
    "kKKKKKPPPPKKKKKk"
    "kKKKKKPPPPKKKKKk"
    "kKKPPKPPPPKPPKKk"
    "kKPPPPPPPPPPPPKk"
    "kKPPPPPPPPPPPPKk"
    "kKPPPPPPPPPPPPKk"
    "kKPPPPPPPPPPPPKk"
    "kKKPPKPPPPKPPKKk"
    "kKKKKKPPPPKKKKKk"
    "kKKKKKPPPPKKKKKk"
    "kKKKKKKPPKKKKKKk"
    "kKKKKKKKKKKKKKKk"
    "kkkkkkkkkkkkkkkk")

$maps["item\town_hall_blueprint"] = @(
    "                "
    "                "
    "  qqqqqqqqqqqq  "
    "  qppppppppppq  "
    "  qpiiiiiiiipq  "
    "  qppppppppppq  "
    "  qpiiiipppppq  "
    "  qppppppppppq  "
    "  qpiiiiiipppq  "
    "  qppppppppppq  "
    "  qpiipppppppq  "
    "  qppppppppppq  "
    "  qqqqqqqqqqqq  "
    "       ww       "
    "       ww       "
    "                ")

$rng = New-Object System.Random 20260905

foreach ($name in $maps.Keys) {
    $rows = $maps[$name]
    if ($rows.Count -ne 16) { throw "$name : $($rows.Count) rows, expected 16" }

    $bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 16; $y++) {
        $row = $rows[$y]
        if ($row.Length -ne 16) { throw "$name row ${y} : $($row.Length) chars, expected 16" }
        for ($x = 0; $x -lt 16; $x++) {
            $ch = $row.Substring($x, 1)
            if ($noise.ContainsKey($ch) -and $rng.NextDouble() -lt 0.18) { $ch = $noise[$ch] }
            if (-not $color.ContainsKey($ch)) { throw "$name : no color for [$ch]" }
            $bmp.SetPixel($x, $y, $color[$ch])
        }
    }
    $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "drawn: $name.png"
}
