"""Prepare UI font weights from the pinned Inter variable font (fonttools 4.67.0).

Usage: python tools/prepare_ui_fonts.py Inter-variable.ttf output-directory
Source revision and license are recorded in the generated assets' NOTICE.txt.
"""
from pathlib import Path
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

source, destination = Path(sys.argv[1]), Path(sys.argv[2])
destination.mkdir(parents=True, exist_ok=True)
for weight, style, filename in [(450, "Regular", "habib-ui-regular.ttf"), (600, "Semibold", "habib-ui-semibold.ttf")]:
    with TTFont(source) as original:
        font = instantiateVariableFont(original, {"wght": weight, "opsz": 14}, inplace=False)
        # Rename derived static instances; retain upstream copyright and license metadata.
        names = {1: "Habib UI", 2: style, 3: "Habib UI " + style + " 1.0", 4: "Habib UI " + style,
                 6: "HabibUI-" + style, 16: "Habib UI", 17: style}
        for record in font["name"].names:
            if record.nameID in names:
                record.string = names[record.nameID].encode(record.getEncoding(), errors="replace")
        font.save(destination / filename)
        font.close()

# Copyright (c) 2026 Bloko. Preparation script licensed under the MIT License.
