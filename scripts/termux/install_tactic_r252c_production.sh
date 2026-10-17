#!/data/data/com.termux/files/usr/bin/bash
# Tactic Keyboard R2.5.2c — Termux one-shot updater and fixture cleanup.
# Must preserve user data and never uninstall tests until production is active.
set -Eeuo pipefail

PKG='com.jorgeprdz.ioskeyboard'
IME="$PKG/helium314.keyboard.latin.LatinIME"
TAG='v4.1-tactic-r252c.1'
APK='Tactic-Keyboard-R252C-PRODUCTION.apk'
SHA='7ece039aa5e0481acb7ab765b51b9ab4423154ff870ce27f2a99c8f17c4db3f0'
BASE="https://github.com/Jorgeprdz/HeliBoard/releases/download/$TAG"

note() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

WORK="$(mktemp -d "${TMPDIR:-$HOME}/tactic-r252c-XXXXXX")"
completed=0
cleanup() {
  local code=$?
  if (( code == 0 && completed == 1 )); then
    rm -rf -- "$WORK"
  else
    printf '\nSe conservaron las aplicaciones instaladas y los archivos de diagnostico: %s\n' "$WORK" >&2
    if (( code == 0 )); then
      printf 'AVISO: el script termino incompleto, sin limpiar apps.\n' >&2
    else
      printf 'Salida con error real: %s\n' "$code" >&2
    fi
  fi
}
trap cleanup EXIT

note '1/7: preparar herramientas'
if ! command -v pkg >/dev/null 2>&1; then die 'Ejecuta este script dentro de Termux, no dentro de adb shell.'; fi
for spec in 'curl:curl' 'adb:android-tools' 'sha256sum:coreutils'; do
  cmd="${spec%%:*}"; package="${spec#*:}"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    pkg install -y "$package" || die "No se pudo instalar $package"
  fi
done

note '2/7: comprobar ADB'
get_devices() {
  adb devices | awk 'NR>1 && $2=="device" {print $1}'
}
DEVICES="$(get_devices)" || die 'ADB fallo al enumerar dispositivos.'
if [[ -z "$DEVICES" ]]; then
  printf 'ADB no tiene dispositivos autorizados:\n'
  adb devices -l || true
  printf '\nEn el S25: Ajustes > Opciones de desarrollador > Depuracion inalambrica.\n'
  printf 'Si ya lo emparejaste, introduce la DIRECCION DE CONEXION IP:PUERTO.\n'
  printf 'Si falta emparejar, primero abre "Vincular dispositivo con codigo" en el telefono.\n'
  read -r -p '¿Emparejar primero? [s/N]: ' PAIR || die 'Falta conexion ADB.'
  if [[ "$PAIR" == 's' || "$PAIR" == 'S' ]]; then
    read -r -p 'Direccion de emparejamiento IP:PUERTO: ' PAIR_HOST || die 'Sin direccion de emparejamiento.'
    read -r -p 'Codigo de emparejamiento: ' PAIR_CODE || die 'Sin codigo de emparejamiento.'
    [[ "$PAIR_HOST" == *:* && -n "$PAIR_CODE" ]] || die 'Direccion o codigo de emparejamiento invalidos.'
    adb pair "$PAIR_HOST" "$PAIR_CODE" || die 'No fue posible emparejar ADB.'
  fi
  read -r -p 'Direccion de conexion ADB IP:PUERTO: ' CONNECT_HOST || die 'Sin direccion de conexion.'
  [[ "$CONNECT_HOST" == *:* ]] || die 'Debes indicar IP:PUERTO de la pantalla principal de Depuracion inalambrica.'
  adb connect "$CONNECT_HOST" || die 'ADB no pudo conectar.'
  DEVICES="$(get_devices)" || die 'ADB fallo al consultar la conexion.'
fi
COUNT="$(printf '%s\n' "$DEVICES" | awk 'NF{n++} END{print n+0}')"
[[ "$COUNT" == 1 ]] || die "Se necesita exactamente un dispositivo autorizado; encontrados: $COUNT. Ejecuta adb devices -l."
SERIAL="$DEVICES"
remote() { adb -s "$SERIAL" shell "$@"; }
MODEL="$(remote getprop ro.product.model | tr -d '\r')"
printf 'Conectado: %s (%s)\n' "$SERIAL" "$MODEL"
[[ "$MODEL" == 'SM-S931B' ]] || die "ADB esta conectado a $MODEL; esperaba el Galaxy S25 SM-S931B."
OLD_IME="$(remote settings get secure default_input_method | tr -d '\r')"
printf 'IME anterior: %s\n' "$OLD_IME"

note '3/7: descargar release oficial'
curl -fL --retry 3 --connect-timeout 20 "$BASE/$APK" -o "$WORK/$APK" || die 'Fallo la descarga del APK.'
curl -fL --retry 3 --connect-timeout 20 "$BASE/SHA256SUMS.txt" -o "$WORK/SHA256SUMS.txt" || die 'Fallo la descarga del SHA256SUMS.'

note '4/7: comprobar dos checksums'
(cd "$WORK" && sha256sum -c SHA256SUMS.txt) || die 'SHA256SUMS del release no coincide.'
ACTUAL="$(sha256sum "$WORK/$APK" | awk '{print $1}')"
[[ "$ACTUAL" == "$SHA" ]] || die 'APK incorrecto: no coincide con el hash verificado del release.'
printf 'SHA256 verificado: %s\n' "$ACTUAL"

note '5/7: actualizar e inspeccionar Tactic Keyboard'
adb -s "$SERIAL" install -r "$WORK/$APK" || die 'Android rechazo la actualizacion. Datos y apps de prueba intactos.'
remote pm path "$PKG" | grep -q '^package:' || die 'APK instalado pero no aparece el paquete de produccion. Sin limpieza.'
remote ime list -a | grep -Fq "$IME" || die 'No esta registrado el servicio IME de produccion. Sin limpieza.'
remote ime enable "$IME" || die 'No se pudo habilitar el teclado nuevo. Sin limpieza.'
remote ime set "$IME" || die 'No se pudo seleccionar el teclado nuevo. Sin limpieza.'
CURRENT="$(remote settings get secure default_input_method | tr -d '\r')"
[[ "$CURRENT" == "$IME" ]] || die 'El IME de produccion no quedo activo. Sin limpieza.'
printf '\033[1;32mActualizacion instalada y seleccionada; datos preservados.\033[0m\n'

note '6/7: limpiar solo las 3 apps de prueba conocidas'
for TEST in 'com.jorgeprdz.ioskeyboard.debug.r252c' 'com.jorgeprdz.r252c.a' 'com.jorgeprdz.r252c.b'; do
  if remote pm path "$TEST" 2>/dev/null | grep -q '^package:'; then
    printf 'Eliminando %s\n' "$TEST"
    if ! adb -s "$SERIAL" uninstall "$TEST"; then
      printf 'AVISO: No se pudo eliminar %s; revisalo manualmente.\n' "$TEST" >&2
    fi
  else
    printf 'Ya ausente: %s\n' "$TEST"
  fi
done
remote settings delete global tactic_r252c_by_app_debug >/dev/null 2>&1 || true

note '7/7: verificacion final'
remote pm path "$PKG" | grep -q '^package:' || die 'Falta el APK final.'
FINAL_IME="$(remote settings get secure default_input_method | tr -d '\r')"
[[ "$FINAL_IME" == "$IME" ]] || die 'El teclado activo cambio durante la limpieza.'
completed=1
printf '\n\033[1;32mTACTIC R2.5.2c PRODUCCION: INSTALADO Y ACTIVO\033[0m\n'
printf 'Abre Tactic Keyboard > Apariencia > Colores dinamicos por aplicacion.\n'
printf 'Activa By App; de fabrica esta OFF. Se conservaron blur, transparencia y ajustes.\n'
