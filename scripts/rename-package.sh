#!/usr/bin/env bash
# Renames the Kotlin package + namespace + applicationId.
#   scripts/rename-package.sh <old.package> <new.package>
# Handles every source set under app/src (main, test, androidTest, debug, release, …), package/import lines,
# app/build.gradle.kts and the Room schema directory (app/schemas/<package>.data.db.AppDatabase).
# Docs / adb commands are NOT rewritten (they need context, e.g. the ".debug" applicationId) — update them by hand.
set -euo pipefail
OLD="${1:?usage: $0 <old.package> <new.package>}"
NEW="${2:?usage: $0 <old.package> <new.package>}"
re='^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'
[[ "$OLD" =~ $re && "$NEW" =~ $re ]] || { echo "invalid package"; exit 1; }
cd "$(dirname "$0")/.."
OLD_PATH="${OLD//.//}"; NEW_PATH="${NEW//.//}"
mv_() { if git rev-parse --is-inside-work-tree >/dev/null 2>&1 && git ls-files --error-unmatch "$1" >/dev/null 2>&1; then git mv "$1" "$2"; else mv "$1" "$2"; fi; }

for set_dir in app/src/*/; do
  for lang in java kotlin; do
    src="${set_dir}${lang}/$OLD_PATH"
    [ -d "$src" ] || continue
    dst="${set_dir}${lang}/$NEW_PATH"
    mkdir -p "$(dirname "$dst")"
    [ -e "$dst" ] && { echo "already exists: $dst"; exit 1; }
    mv_ "$src" "$dst"
    find "${set_dir}${lang}" -type d -empty -delete
    echo "moved $src -> $dst"
  done
done

schema_old="app/schemas/$OLD.data.db.AppDatabase"
if [ -d "$schema_old" ]; then
  mv_ "$schema_old" "app/schemas/$NEW.data.db.AppDatabase"
  echo "moved $schema_old -> app/schemas/$NEW.data.db.AppDatabase"
fi

grep -rlF "$OLD" app/src app/build.gradle.kts app/schemas | while read -r f; do
  sed -i.bak "s/${OLD//./\\.}/$NEW/g" "$f" && rm "$f.bak"
  echo "rewrote $f"
done
echo "Renamed $OLD -> $NEW. Now update docs / adb commands by hand and grep for '$OLD'."
