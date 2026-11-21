#!/bin/bash
# Compiles the round-3 PDF code (text layer, bookmarks, notes, image-keeping compress, rearrange,
# fonts, coordinate mapping, card layout, page list) against desktop PDFBox 2.0.27 plus one driver,
# and runs it. Desktop PDFBox has the same API as PDFBox-Android under org.apache.pdfbox instead of
# com.tom_roush.pdfbox, which is all the sed below changes.
#
# usage: build_tc3.sh DriverFileName(no .kt) MainClassName
#
# Override these if your layout differs from the one this was built and run on:
#   JASCANNER_SRC   - path to app/src/main/java/aktarjabed/com/jaascanner
#   GRADLE_LIB      - a Gradle distribution's lib/ folder (supplies the Kotlin compiler, stdlib,
#                      coroutines and annotations jars this was run with: kotlinc 2.0.21,
#                      kotlinx-coroutines-core-jvm 1.6.4)
#   PDFBOX_CLASSES  - a directory of .class files for desktop Apache PDFBox 2.0.27, built from
#                      source (or unjarred from the pdfbox-app/pdfbox/fontbox jars)
#   ANDROID_JAR     - an android.jar stub (real method signatures, stub bodies) to compile the
#                      pdf/*.kt files' android.graphics.* calls against, e.g. the "android-36"
#                      jar from the Sable/android-platforms repository used elsewhere in this audit
#   WORK_DIR        - scratch directory for generated sources and class files (default: ./out)
set -e
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JASCANNER_SRC="${JASCANNER_SRC:-$HERE/../../../app/src/main/java/aktarjabed/com/jaascanner}"
GRADLE_LIB="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"
PDFBOX_CLASSES="${PDFBOX_CLASSES:-$HERE/pdfbox-classes}"
ANDROID_JAR="${ANDROID_JAR:-$HERE/android.jar}"
WORK_DIR="${WORK_DIR:-$HERE/out}"

T="$WORK_DIR"; L="$GRADLE_LIB"; R="$JASCANNER_SRC"
rm -rf "$T/src" "$T/out"; mkdir -p "$T/src" "$T/out"
for f in pdf/PdfTextLayer pdf/PdfBookmarks pdf/PdfNotes pdf/PdfImageCompress pdf/PdfTools pdf/SystemFonts core/FontGuess core/PdfCoordinateMapper core/CardLayout core/PageList; do
  sed -e 's/com\.tom_roush\.pdfbox/org.apache.pdfbox/g' -e '/graphics\.image\.JPEGFactory/d' "$R/$f.kt" > "$T/src/$(echo "$f" | tr / _).kt"
done
PB="$PDFBOX_CLASSES:/usr/share/java/commons-logging.jar"
CP="$L/kotlin-stdlib-2.0.21.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar:$PB:$ANDROID_JAR"
java -cp "$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -cp "$CP" -d "$T/out" \
  "$T"/src/*.kt "$HERE/kstub_Stubs.kt" "$HERE/Stubs3.kt" "$HERE/drivers/$1.kt" 2>&1 | grep -v "^Picked up" || true
shift
cd "$T"
java -cp "$T/out:$CP" "$1" 2>&1 | grep -v "^Picked up"
