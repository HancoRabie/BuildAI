#!/usr/bin/env sh
set -eu
GRADLE_VERSION=8.10.2
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_HOME="$BASE_DIR/.gradle-bootstrap/gradle-$GRADLE_VERSION"
ZIP_FILE="$BASE_DIR/.gradle-bootstrap/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$BASE_DIR/.gradle-bootstrap"
  curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
  unzip -q -o "$ZIP_FILE" -d "$BASE_DIR/.gradle-bootstrap"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
