#!/usr/bin/env bash
# Valida, com um FFmpeg real, o desenho das expressões geradas por FocusCropExpression:
# parse do filtro, recorte centrado no rosto, limite nas bordas, interpolação e `t` relativo ao clipe.
# Uso: tools/ffmpeg-validation/validate_crop.sh   (requer ffmpeg, ffprobe e python3 no PATH)
set -euo pipefail
cd "$(dirname "$0")"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
ffmpeg -v error -y -f lavfi -i "testsrc2=size=1920x1080:rate=10:duration=3" -pix_fmt yuv420p "$WORK/src.mp4"
mapfile -t G < <(python3 crop_expression_mirror.py)
# Largura do recorte que o FFmpeg realmente usa (min(iw, ih*1080/1920) = 607.5 -> 608).
ffmpeg -v error -y -i "$WORK/src.mp4" -vf "${G[0]}" -frames:v 1 "$WORK/w.png"
OW=$(ffprobe -v error -show_entries stream=width -of csv=p=0 "$WORK/w.png")
fail=0
check() { # nome, filtro_golden, frame, x_esperado
  ffmpeg -v error -y -i "$WORK/src.mp4" -vf "select=eq(n\,$3),$2" -vsync 0 -frames:v 1 "$WORK/a.png"
  ffmpeg -v error -y -i "$WORK/src.mp4" -vf "select=eq(n\,$3),crop=$OW:1080:$4:0" -vsync 0 -frames:v 1 "$WORK/b.png"
  if cmp -s "$WORK/a.png" "$WORK/b.png"; then echo "OK   $1 (x=$4)"; else echo "FAIL $1 (esperado x=$4)"; fail=1; fi
}
X() { python3 -c "import sys;ow=$OW;c=float(sys.argv[1]);print(int(max(0,min(1920-ow,c*1920-ow/2))))" "$1"; }
check "sem trilha: centralizado"            "${G[0]}" 0  "$(( (1920-OW)/2 ))"
check "t=0   centro 0.2"                    "${G[1]}" 0  "$(X 0.2)"
check "t=1.0 interpolado (0.5)"             "${G[1]}" 10 "$(X 0.5)"
check "t=2.5 após o último ponto (0.8)"     "${G[1]}" 25 "$(X 0.8)"
check "t=0   centro 0.9 limitado à borda"   "${G[2]}" 0  "$(X 0.9)"
exit $fail
