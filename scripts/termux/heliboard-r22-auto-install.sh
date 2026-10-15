#!/data/data/com.termux/files/usr/bin/bash
# HeliBoard R2.2: download and update only after a successful, pinned GitHub gate.
set -euo pipefail
repo=Jorgeprdz/HeliBoard
branch=feat/heliboard-ai-r2-2-autocorrect-expressive
workflow=build-debug-apk.yml
artifact=HeliBoard-R2.2-debug-apk
package=com.jorgeprdz.ioskeyboard.debug
run= expected= serial= dry=false once=false timeout=3600
started=$SECONDS
work=
logdir="${XDG_STATE_HOME:-$HOME/.local/state}/heliboard-r22"
mkdir -p "$logdir"
log="$logdir/install.log"
say() { printf '\033[36m[R2.2 +%ss]\033[0m %s\n' "$((SECONDS-started))" "$*"; printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" >> "$log"; }
fail() { say "BLOCKED: $*"; exit 1; }
cleanup() { if [[ -n "$work" && -d "$work" && "$work" == */heliboard-r22.* ]]; then rm -rf -- "$work"; fi; }
trap cleanup EXIT
while (($#)); do
 case "$1" in
 --run-id) run="$2"; shift 2;; --sha) expected="$2"; shift 2;;
 --serial) serial="$2"; shift 2;; --timeout) timeout="$2"; shift 2;;
 --watch) shift;; --once) once=true; shift;; --dry-run) dry=true; shift;;
 *) fail "Opción desconocida: $1";;
 esac
done
[[ "$expected" =~ ^[0-9a-f]{40}$ ]] || fail "Indica --sha con el commit exacto publicado."
[[ "$timeout" =~ ^[0-9]+$ && "$timeout" -gt 0 && "$timeout" -le 86400 ]] || fail "Timeout inválido."
for tool in gh adb jq sha256sum unzip aapt apksigner timeout; do
 command -v "$tool" >/dev/null || fail "Falta $tool. Instala en Termux: pkg install gh android-tools jq coreutils unzip aapt apksigner"
done
gh auth status >/dev/null 2>&1 || fail "Ejecuta gh auth login en Termux."
gh api "repos/$repo/actions/permissions" --jq '.enabled' | grep -qx true || fail "GitHub Actions deshabilitado o sin acceso."
workflow_id=$(gh api "repos/$repo/actions/workflows/$workflow" --jq .id)
if [[ -z "$run" ]]; then
 run=$(gh api "repos/$repo/actions/workflows/$workflow/runs?branch=$branch&head_sha=$expected&per_page=100" --jq '.workflow_runs | map(select(.conclusion=="success")) | sort_by(.run_number) | last | .id // empty')
 [[ -n "$run" ]] || fail "No existe un run exitoso para la rama y SHA exactos. Usa --run-id para esperar uno ya despachado."
fi
[[ "$run" =~ ^[0-9]+$ ]] || fail "Run ID inválido."
while :; do
 (( SECONDS-started < timeout )) || fail "Timeout esperando CI."
 info=$(gh api "repos/$repo/actions/runs/$run")
 jq -e --arg sha "$expected" --arg branch "$branch" --arg repo "$repo" --argjson workflow "$workflow_id"  '.head_sha==$sha and .head_branch==$branch and .repository.full_name==$repo and .workflow_id==$workflow' <<< "$info" >/dev/null || fail "Run de otro repositorio/workflow/rama/SHA."
 status=$(jq -r .status <<< "$info")
 conclusion=$(jq -r .conclusion <<< "$info")
 say "Run $run [$status/$conclusion] ▰▰▱"
 if [[ "$status" == completed ]]; then
  [[ "$conclusion" == success ]] || fail "CI terminó $conclusion; no se descarga ni instala."
  break
 fi
 $once && fail "Run pendiente (--once)."
 sleep 20
done
work=$(mktemp -d "${TMPDIR:-/data/data/com.termux/files/usr/tmp}/heliboard-r22.XXXXXXXX")
gh run download "$run" --repo "$repo" -n "$artifact" -D "$work"
[[ $(find "$work" -mindepth 1 | wc -l) -eq 3 ]] || fail "Contenido inesperado en artifact."
for file in HeliBoard-R2.2-debug.apk SHA256SUMS.txt build-manifest.json; do
 [[ -s "$work/$file" && -f "$work/$file" && ! -L "$work/$file" ]] || fail "Artifact incompleto o enlace inesperado."
done
manifest="$work/build-manifest.json"
jq -e --arg sha "$expected" --arg run "$run" --arg package "$package"  '.commit==$sha and .runId==$run and .package==$package and .apk=="HeliBoard-R2.2-debug.apk" and (.versionCode|type=="number")' "$manifest" >/dev/null || fail "Manifest no coincide."
apk="$work/HeliBoard-R2.2-debug.apk"
hash=$(jq -r .sha256 "$manifest")
cert=$(jq -r .certificateSha256 "$manifest" | tr '[:upper:]' '[:lower:]' | tr -d :)
[[ "$hash" =~ ^[0-9a-f]{64}$ && "$cert" =~ ^[0-9a-f]{64}$ ]] || fail "Hashes inválidos."
[[ $(cat "$work/SHA256SUMS.txt") == "$hash  HeliBoard-R2.2-debug.apk" ]] || fail "Lista checksum inesperada."
(cd "$work" && sha256sum -c SHA256SUMS.txt) || fail "SHA-256 incorrecto."
unzip -tq "$apk" >/dev/null || fail "APK corrupto."
badging=$(aapt dump badging "$apk")
actual_package=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")
version=$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")
[[ "$actual_package" == "$package" && "$version" =~ ^[0-9]+$ && "$version" == "$(jq -r .versionCode "$manifest")" ]] || fail "Paquete/versión incorrectos."
signature=$(apksigner verify --print-certs "$apk") || fail "Firma APK inválida."
actual_cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$signature" | tr '[:upper:]' '[:lower:]' | tr -d :)
[[ "$actual_cert" == "$cert" ]] || fail "Certificado no coincide con manifest."
mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -n "$serial" ]]; then
 printf '%s\n' "${devices[@]}" | grep -Fxq "$serial" || fail "Serial seleccionado sin autorización ADB."
else
 [[ ${#devices[@]} -eq 1 ]] || fail "Se necesita un dispositivo ADB autorizado; si hay varios usa --serial. Empareja previamente en Depuración inalámbrica con adb pair y adb connect."
 serial="${devices[0]}"
fi
model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
[[ "$model" == SM-S931* ]] || fail "Modelo $model no corresponde al Galaxy S25 esperado."
installed=$(adb -s "$serial" shell pm path "$package" | tr -d '\r')
[[ "$installed" == package:*/base.apk* ]] || fail "No hay instalación previa identificable; se requiere actualizar conservando datos."
path=$(sed -n 's/^package://p' <<< "$installed" | head -1)
adb -s "$serial" pull "$path" "$work/installed.apk" >/dev/null || fail "No se puede inspeccionar APK instalado."
old_badging=$(aapt dump badging "$work/installed.apk")
old_version=$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<< "$old_badging")
[[ "$old_version" =~ ^[0-9]+$ && "$version" -ge "$old_version" ]] || fail "Downgrade rechazado."
old_signature=$(apksigner verify --print-certs "$work/installed.apk") || fail "No se pudo validar certificado instalado."
old_cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$old_signature" | tr '[:upper:]' '[:lower:]' | tr -d :)
[[ "$old_cert" == "$cert" ]] || fail "Firma incompatible con instalación existente. Conserva datos y recupera la clave original."
say "Gate, SHA, firma, paquete y versión verificados. Run $run, Galaxy $model."
$dry && { say "DRY-RUN PASS: instalación no ejecutada."; exit 0; }
timeout 180 adb -s "$serial" install -r "$apk" || fail "ADB rechazó actualización. No se desinstaló ni borró datos."
post=$(adb -s "$serial" shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p' | head -1)
[[ -n "$post" ]] || fail "Postinstalación: paquete ausente."
adb -s "$serial" pull "$post" "$work/post.apk" >/dev/null
[[ $(sha256sum "$work/post.apk" | cut -d' ' -f1) == "$hash" ]] || fail "Postinstalación: hash diferente."
say "INSTALLED & VERIFIED: $package versionCode=$version run=$run SHA=$hash"
say "Pendiente aceptación física: Gemini claro/oscuro, Backspace, escritura y DeX."
