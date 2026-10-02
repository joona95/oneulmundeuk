#!/usr/bin/env bash
# Renames the placeholder package/applicationId once the app name is decided.
#   scripts/rename-package.sh com.example.realname
# Moves sources in main/test/androidTest, rewrites package/import lines, namespace and applicationId.
set -euo pipefail
OLD="app.placeholder.journal"
NEW="${1:?usage: $0 <new.package.name>}"
[[ "$NEW" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo "invalid package: $NEW"; exit 1; }
cd "$(dirname "$0")/.."
OLD_PATH="${OLD//.//}"; NEW_PATH="${NEW//.//}"
for set in main test androidTest; do
  src="app/src/$set/java/$OLD_PATH"
  [ -d "$src" ] || continue
  mkdir -p "app/src/$set/java/$NEW_PATH"
  cp -R "$src/." "app/src/$set/java/$NEW_PATH/"
  rm -rf "$src"
  find "app/src/$set/java" -type d -empty -delete
done
grep -rl "$OLD" app/src app/build.gradle.kts | while read -r f; do
  sed -i.bak "s/${OLD//./\\.}/$NEW/g" "$f" && rm "$f.bak"
done
echo "Renamed $OLD -> $NEW. Also update rootProject.name (settings.gradle.kts) and app_name (strings.xml)."
