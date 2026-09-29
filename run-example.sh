#!/bin/sh
set -eu
classes="${TMPDIR:-/tmp}/legal-session-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" legalvideo.LegalSessionApplication
