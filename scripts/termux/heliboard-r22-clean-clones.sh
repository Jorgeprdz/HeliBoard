#!/data/data/com.termux/files/usr/bin/bash
# Preview first; remove only confirmed, fully published HeliBoard clones.
set -euo pipefail
for tool in git realpath find; do command -v "$tool" >/dev/null || { echo "Falta $tool"; exit 1; }; done
declare -a candidates=() safe=()
if (($#)); then
 candidates=("$@")
else
 while IFS= read -r -d '' entry; do candidates+=("${entry%/.git}"); done < <(
  find "$HOME" -maxdepth 4 -type d -name .git -prune -print0 2>/dev/null
 )
fi
eligible() {
 local path="$1" remote head
 [[ -d "$path/.git" && ! -L "$path" ]] || return 1
 remote=$(git -C "$path" remote get-url origin 2>/dev/null) || return 1
 case "$remote" in
 https://github.com/Jorgeprdz/HeliBoard|https://github.com/Jorgeprdz/HeliBoard.git|git@github.com:Jorgeprdz/HeliBoard.git|https://github.com/HeliBorg/HeliBoard|https://github.com/HeliBorg/HeliBoard.git|git@github.com:HeliBorg/HeliBoard.git) ;;
 *) return 1;;
 esac
 [[ -z "$(git -C "$path" status --porcelain=v1 -uall)" ]] || { echo "CONSERVADO cambios locales: $path"; return 1; }
 [[ -z "$(git -C "$path" rev-list --reflog --all HEAD --not --remotes)" ]] || { echo "CONSERVADO commits no publicados: $path"; return 1; }
 [[ -z "$(git -C "$path" stash list)" ]] || { echo "CONSERVADO stash: $path"; return 1; }
 [[ $(git -C "$path" worktree list --porcelain | grep -c '^worktree ') -eq 1 ]] || { echo "CONSERVADO worktrees: $path"; return 1; }
 # Ignored files can hold unique content. Retain the entire clone if any exist.
 [[ -z "$(git -C "$path" ls-files --others --ignored --exclude-standard)" ]] || { echo "CONSERVADO archivos ignorados que requieren auditoría: $path"; return 1; }
 [[ -z "$(find "$path" -path "$path/.git" -prune -o -type f \( -iname '*.zip' -o -iname '*.p12' -o -iname '*.jks' -o -iname '*.keystore' \) -print -quit)" ]] || { echo "CONSERVADO respaldo/clave: $path"; return 1; }
 head=$(git -C "$path" rev-parse HEAD)
 git ls-remote "$remote" | awk '{print $1}' | grep -Fxq "$head" || { echo "CONSERVADO HEAD no confirmado en GitHub: $path"; return 1; }
}
for candidate in "${candidates[@]}"; do
 [[ ! -L "$candidate" ]] || { echo "CONSERVADO enlace: $candidate"; continue; }
 path=$(realpath -e -- "$candidate") || continue
 case "$path" in /|/workspace|/root|"$HOME"|/storage/emulated/0|/sdcard|*/.local/share/heliboard-signing*) echo "CONSERVADO ruta protegida: $path"; continue;; esac
 if eligible "$path"; then safe+=("$path"); echo "ELIMINABLE (clon limpio, publicado): $path"; fi
done
(("${#safe[@]}")) || { echo "Sin clones elegibles. Residuos sueltos conservados: no se puede confirmar remoto mediante Git."; exit 0; }
echo "Se eliminarán únicamente las rutas ELIMINABLE. Claves, ZIP, cachés compartidas y otros proyectos quedan protegidos."
read -r -p "Escribe ELIMINAR R2.2 para confirmar: " answer
[[ "$answer" == 'ELIMINAR R2.2' ]] || { echo "Limpieza cancelada; no se eliminó nada."; exit 0; }
for path in "${safe[@]}"; do
 if eligible "$path"; then
  rm -rf -- "$path"
  echo "ELIMINADO: $path (código recuperable de GitHub; clon sin contenido único detectado)"
 else echo "CONSERVADO: cambió desde la previsualización: $path"; fi
done
