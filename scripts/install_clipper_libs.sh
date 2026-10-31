#!/usr/bin/env bash
# Round 80: install the two third-party Java libraries behind port.natives.ClipperEngine into the
# project-local Maven repository (.toolchain/m2). Downloads go to .toolchain/downloads and are
# checked against pinned sha256 sums; nothing global is touched.
#  - Clipper2-java 2.0.1 (micycle1, Boost Software License 1.0), jitpack build.
#  - earcut4j 2.2.2 (ISC, port of Mapbox earcut), Maven Central.
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
dl="$PZ_WEB_ROOT/.toolchain/downloads/clipper"
mkdir -p "$dl"
fetch() { # url file sha256
  [ -f "$dl/$2" ] || curl -fsSL -o "$dl/$2" "$1"
  echo "$3  $dl/$2" | sha256sum -c --quiet
}
fetch https://jitpack.io/com/github/micycle1/Clipper2-java/2.0.1/Clipper2-java-2.0.1.jar \
  Clipper2-java-2.0.1.jar fbbeee0bb649b96cc98d0b226a741c8c3d221017fbf068c8740c3dff0be3da57
fetch https://repo1.maven.org/maven2/io/github/earcut4j/earcut4j/2.2.2/earcut4j-2.2.2.jar \
  earcut4j-2.2.2.jar aa4c8f8d0463aed68e07b9445791d2830281458e88695f1dffbf7905cac4356e
install() { # group artifact version file
  mvn -B -q -ntp -Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2" install:install-file \
    -Dfile="$dl/$4" -DgroupId="$1" -DartifactId="$2" -Dversion="$3" -Dpackaging=jar -DgeneratePom=true
}
install com.github.micycle1 Clipper2-java 2.0.1 Clipper2-java-2.0.1.jar
install io.github.earcut4j earcut4j 2.2.2 earcut4j-2.2.2.jar
echo "Clipper libraries installed in .toolchain/m2"
