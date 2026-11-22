#!/usr/bin/env bash
# Compiles the round-3 PDF code (text layer, bookmarks, notes, image-keeping compress, rearrange,
# fonts, coordinate mapping, card layout, page list) against desktop PDFBox 2.0.27 plus one driver,
# and runs it. Desktop PDFBox has the same API as PDFBox-Android under org.apache.pdfbox instead of
# com.tom_roush.pdfbox, which is all the sed below changes.
#
# This is a desktop PDF check, not an app build. It cannot exercise Android UI, PdfRenderer, ML Kit
# or the Android bitmap recoder.
#
# usage: build_tc3.sh DriverFileName(no .kt) MainClassName
#
# All versions and paths can be overridden:
#   JASCANNER_SRC       path to app/src/main/java/aktarjabed/com/jaascanner
#   GRADLE_LIB          a Gradle distribution's lib/ folder
#   KOTLIN_VERSION      Kotlin compiler/stdlib version            (default: 2.3.21)
#   COROUTINES_VERSION  kotlinx-coroutines-core-jvm version       (default: 1.10.2)
#   ANNOTATIONS_VERSION JetBrains annotations version             (default: 24.0.1)
#   PDFBOX_CLASSES      directory of desktop Apache PDFBox 2.0.27 class files
#   ANDROID_JAR         android.jar stub with real method signatures
#   WORK_DIR            scratch directory                         (default: ./out)
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 DriverFileName(no .kt) MainClassName" >&2
  exit 2
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JASCANNER_SRC="${JASCANNER_SRC:-$HERE/../../../app/src/main/java/aktarjabed/com/jaascanner}"
GRADLE_LIB="${GRADLE_LIB:-/opt/gradle-9.6.0/lib}"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.21}"
COROUTINES_VERSION="${COROUTINES_VERSION:-1.10.2}"
ANNOTATIONS_VERSION="${ANNOTATIONS_VERSION:-24.0.1}"
PDFBOX_CLASSES="${PDFBOX_CLASSES:-$HERE/pdfbox-classes}"
ANDROID_JAR="${ANDROID_JAR:-$HERE/android.jar}"
WORK_DIR="${WORK_DIR:-$HERE/out}"

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "build_tc3.sh: missing required file: $1" >&2
    echo "build_tc3.sh: set GRADLE_LIB, PDFBOX_CLASSES or ANDROID_JAR" >&2
    exit 2
  fi
}

T="$WORK_DIR"
L="$GRADLE_LIB"
R="$JASCANNER_SRC"
KOTLIN_COMPILER="$L/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar"
KOTLIN_STDLIB="$L/kotlin-stdlib-$KOTLIN_VERSION.jar"
COROUTINES="$L/kotlinx-coroutines-core-jvm-$COROUTINES_VERSION.jar"
ANNOTATIONS="$L/annotations-$ANNOTATIONS_VERSION.jar"

require_file "$KOTLIN_COMPILER"
require_file "$KOTLIN_STDLIB"
require_file "$COROUTINES"
require_file "$ANNOTATIONS"
require_file "$ANDROID_JAR"

rm -rf "$T/src" "$T/out"
mkdir -p "$T/src" "$T/out"
for f in pdf/PdfTextLayer pdf/PdfBookmarks pdf/PdfNotes pdf/PdfImageCompress pdf/PdfTools pdf/SystemFonts core/FontGuess core/PdfCoordinateMapper core/CardLayout core/PageList; do
  sed -e 's/com\.tom_roush\.pdfbox/org.apache.pdfbox/g' -e '/graphics\.image\.JPEGFactory/d' "$R/$f.kt" > "$T/src/$(echo "$f" | tr / _).kt"
done

PB="$PDFBOX_CLASSES:/usr/share/java/commons-logging.jar"
CP="$KOTLIN_STDLIB:$COROUTINES:$ANNOTATIONS:$PB:$ANDROID_JAR"
COMPILER_CP="$KOTLIN_COMPILER:$KOTLIN_STDLIB:$ANNOTATIONS:$COROUTINES"
if [[ -f "$L/kotlin-reflect-$KOTLIN_VERSION.jar" ]]; then
  COMPILER_CP="$COMPILER_CP:$L/kotlin-reflect-$KOTLIN_VERSION.jar"
fi
if [[ -f "$L/trove4j-1.0.20200330.jar" ]]; then
  COMPILER_CP="$COMPILER_CP:$L/trove4j-1.0.20200330.jar"
fi

DRIVER="$1"
MAIN_CLASS="$2"
COMPILE_LOG="$T/compile.log"
if ! java -cp "$COMPILER_CP" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -cp "$CP" -d "$T/out" \
  "$T"/src/*.kt "$HERE/kstub_Stubs.kt" "$HERE/Stubs3.kt" "$HERE/drivers/$DRIVER.kt" >"$COMPILE_LOG" 2>&1
then
  cat "$COMPILE_LOG" >&2
  echo "build_tc3.sh: Kotlin compilation failed" >&2
  exit 1
fi
sed '/^Picked up/d' "$COMPILE_LOG" >&2

cd "$T"
java -cp "$T/out:$CP" "$MAIN_CLASS" 2>&1 | sed '/^Picked up/d'
