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
$hex.Add("b","FFB9A47A")   # burlap, light
$hex.Add("B","FF97815A")   # burlap, shaded
$hex.Add("t","FF6E5B38")   # rope
$hex.Add("r","FF8A7A5C")   # washing line
$hex.Add("W","FFE8E4DA")   # linen
$hex.Add("V","FFCFC9BC")   # linen, shaded
$hex.Add("u","FF7C93AE")   # dyed cloth
$hex.Add("n","FF5A4632")   # bark
$hex.Add("N","FF6E5540")   # bark, lit
$hex.Add("=","FF5E4531")   # beam, lit edge
$hex.Add("_","FF3A281B")   # beam, shadowed edge
$hex.Add("'","FFE6DCC6")   # plaster, lit
$hex.Add("h","FFC9B78F")   # burlap, lit
$hex.Add("v","FF47372A")   # bark, shadow
$hex.Add("j","FFC79E5E")   # cut wood, lit
$hex.Add("y","FFB8B2A4")   # linen, shadow
$hex.Add("U","FF6A7F98")   # dyed cloth, shadow
$hex.Add("1","FFE7DCC0")   # maya limestone
$hex.Add("2","FFD5C8A8")   # maya limestone, shaded
$hex.Add("3","FFF2EAD6")   # maya limestone, sun-bleached
$hex.Add("4","FFB4503A")   # maya ochre red
$hex.Add("5","FF8E3B2B")   # maya ochre red, deep
$hex.Add("6","FF4E8C6A")   # maya jade
$hex.Add("7","FFC2A65C")   # palm thatch, light
$hex.Add("8","FFA88C46")   # palm thatch, mid
$hex.Add("9","FF7E6832")   # palm thatch, dark
$hex.Add("o","FFE0D8C4")   # carved stone, lit
$hex.Add("O","FF9E9686")   # carved stone, shadow
$hex.Add("Q","FFCFC6B0")   # carved stone
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
$noise.Add("n", "N")
$noise.Add("W", "V")
$noise.Add("4", "5")
$noise.Add("7", "8")
$noise.Add("o", "Q")

$maps = [ordered]@{}

$maps["block\town_hall_side"] = @(
    "################"
    "#=============_#"
    "#'##.......##',#"
    "#.,'##...##'..,#"
    "#..,.'#.#'.,...#"
    "#,..,.###.,..,.#"
    "#..,..'#'..,...#"
    "#.,..'#.#'..,,.#"
    "#..,'#...#'.,..#"
    "#.,'#.,,.,#'...#"
    "#,'#..,..,.#',.#"
    "#'#.,...,.,.#,,#"
    "##.,..,...,..##,"
    "#,..,...,..,..,#"
    "#______________#"
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

# --- decor blocks: the Norman village gets its own material ---

# Half-timbering: a beam frame with a single stud, so a wall of these tiles
# into a proper timbered facade instead of a flat sheet.
$maps["block\timber_frame"] = @(
    "################"
    "#======#=======#"
    "#''''''#'''''''#"
    "#......#.......#"
    "#......#.......#"
    "#,,,,,,#,,,,,,,#"
    "#______#_______#"
    "################"
    "#======#=======#"
    "#''''''#'''''''#"
    "#......#.......#"
    "#......#.......#"
    "#,,,,,,#,,,,,,,#"
    "#,,,,,,#,,,,,,,#"
    "#______#_______#"
    "################")

# Bare plaster. Flat on purpose: the deterministic noise does the mottling,
# and a pattern here would fight the timbering next to it.
$maps["block\plaster"] = @(
    "'..,..'...,..'.."
    "..'...,..'....,."
    ",..'....,...'..."
    "..,...'...,....'"
    "'...,....'...,.."
    "...'...,....'..."
    ".,....'...,....,"
    "..,'.....,...'.."
    "'....,..'.....,."
    "..'....,...'...."
    ",...'.....,...'."
    "...,...'....,..."
    "'.....,...'....."
    "..,'....,....'.."
    ".'...,...'...,.."
    "...'....,...'...")

$maps["block\firewood_side"] = @(
    "nnnnnnnnnnnnnnnn"
    "nNnNnnNnnnNnnNnn"
    "nnvnnnnvnnnnvnnn"
    "dddddddddddddddd"
    "nnnnnnnnnnnnnnnn"
    "nNnnNnnnNnnNnnNn"
    "nvnnnnvnnnnvnnnn"
    "dddddddddddddddd"
    "nnnnnnnnnnnnnnnn"
    "nNnNnnNnnnNnnNnn"
    "nnvnnnnvnnnnvnnn"
    "dddddddddddddddd"
    "nnnnnnnnnnnnnnnn"
    "nNnnNnnnNnnNnnNn"
    "nvnnnnvnnnnvnnnn"
    "dddddddddddddddd")

# Cut ends of the bundle: sixteen logs looked at end-on.
$maps["block\firewood_end"] = @(
    "dddddddddddddddd"
    "djLddjLddjLddjLd"
    "dLMddLMddLMddLMd"
    "dddddddddddddddd"
    "dddddddddddddddd"
    "djLddjLddjLddjLd"
    "dLMddLMddLMddLMd"
    "dddddddddddddddd"
    "dddddddddddddddd"
    "djLddjLddjLddjLd"
    "dLMddLMddLMddLMd"
    "dddddddddddddddd"
    "dddddddddddddddd"
    "djLddjLddjLddjLd"
    "dLMddLMddLMddLMd"
    "dddddddddddddddd")

# Washing on a line: a rope across the top and two cloths hanging off it.
# Transparent everywhere else, so the cross model reads as cloth, not a bush.
$maps["block\laundry"] = @(
    "rrrrrrrrrrrrrrrr"
    " WyWWW   uUuuu  "
    " WyWWW   uUuuu  "
    " WyWWV   uUuuU  "
    " WyWWW   uUuuu  "
    " WyWWW   uUuuu  "
    " WyWWV   uUuuU  "
    " WyWWW   uUuuu  "
    " WyWWW   uUuuu  "
    " WyWWV   uUuuU  "
    " WyWWW   uUuuu  "
    " WVWWW   uUuuu  "
    " VVWWV   uUUuU  "
    "  WVW     uUu   "
    "                "
    "                ")

# Sacking with two rope bands. Grain, flour, whatever the colony hauls.
$maps["block\grain_sack"] = @(
    "hhhhhhhhhhhhhhhh"
    "bBbBbBbBbBbBbBbB"
    "tttttttttttttttt"
    "tttttttttttttttt"
    "bBbBbBbBbBbBbBbB"
    "BbBbBbBbBbBbBbBb"
    "bBbBbBbBbBbBbBbB"
    "BbBbBbBbBbBbBbBb"
    "bBbBbBbBbBbBbBbB"
    "tttttttttttttttt"
    "tttttttttttttttt"
    "bBbBbBbBbBbBbBbB"
    "BbBbBbBbBbBbBbBb"
    "BbBbBbBbBbBbBbBb"
    "BBBBBBBBBBBBBBBB"
    "BBBBBBBBBBBBBBBB")

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

$maps["block\ochre_plaster"] = @(
    "3333333333333333"
    "4444444444444444"
    "4454444444444544"
    "4444444544444444"
    "4444444444544444"
    "4544444444444454"
    "4444454444444444"
    "4444444444445444"
    "4441444454444444"
    "4444444444444544"
    "4544444444444444"
    "4444444544414444"
    "4444444444444454"
    "4454444444444444"
    "4444444444544444"
    "5555555555555555")

$maps["block\carved_stone"] = @(
    "OOOOOOOOOOOOOOOO"
    "oooooooooooooooo"
    "ooooooo66ooooooo"
    "oooooo4444oooooo"
    "ooooo444444ooooo"
    "oooo44444444oooo"
    "oooooooooooooooo"
    "ooo4444444444ooo"
    "oo444444444444oo"
    "oooooooooooooooo"
    "o44444444444444o"
    "4444444444444444"
    "oooooooooooooooo"
    "QQQQQQQQQQQQQQQQ"
    "oooooooooooooooo"
    "OOOOOOOOOOOOOOOO")

$maps["block\thatch"] = @(
    "7897789877897898"
    "7897789877897898"
    "7897789877897898"
    "9897789877897898"
    "7897789977897898"
    "7897789877897998"
    "7897789877897898"
    "7997789877897898"
    "7897789877997898"
    "7897789877897898"
    "7897989877897898"
    "7897789877897898"
    "7897789877897899"
    "7897789877897898"
    "9897789877897898"
    "7897789877897898")

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
