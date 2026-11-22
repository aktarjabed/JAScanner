#!/usr/bin/env bash
# Pure-logic check only: compiles app/src/main/.../core (minus the two Android-only files)
# plus every unit test against a plain Kotlin compiler, and runs them under JUnit 4.
# No Android Gradle plugin, no emulator, no PDFBox: this proves the pure Kotlin logic, nothing
# about the Android UI.
#
# usage: run_unit.sh [tailLines]
#
# All versions and paths can be overridden:
#   JASCANNER_SRC       path to app/src
#   GRADLE_LIB          a Gradle distribution's lib/ folder (compiler, stdlib, coroutines, annotations)
#   KOTLIN_VERSION      Kotlin compiler/stdlib version            (default: 2.3.21)
#   COROUTINES_VERSION  kotlinx-coroutines-core-jvm version       (default: 1.10.2)
#   JUNIT_VERSION       JUnit 4 version                           (default: 4.13.2)
#   HAMCREST_VERSION    Hamcrest core version                     (default: 1.3)
#   ANNOTATIONS_VERSION JetBrains annotations version             (default: 24.0.1)
#   JUNIT_JAR, HAMCREST_JAR  full paths, when they are not in GRADLE_LIB
#   WORK_DIR            scratch directory                         (default: ./out)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JASCANNER_SRC="${JASCANNER_SRC:-$HERE/../../../app/src}"
GRADLE_LIB="${GRADLE_LIB:-/opt/gradle-9.6.0/lib}"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.3.21}"
COROUTINES_VERSION="${COROUTINES_VERSION:-1.10.2}"
JUNIT_VERSION="${JUNIT_VERSION:-4.13.2}"
HAMCREST_VERSION="${HAMCREST_VERSION:-1.3}"
ANNOTATIONS_VERSION="${ANNOTATIONS_VERSION:-24.0.1}"
WORK_DIR="${WORK_DIR:-$HERE/out}"

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "run_unit.sh: missing required file: $1" >&2
    echo "run_unit.sh: set GRADLE_LIB or the matching *_JAR / *_VERSION variable" >&2
    exit 2
  fi
}

L="$GRADLE_LIB"
R="$JASCANNER_SRC"
OUT="$WORK_DIR"
rm -rf "$OUT"
mkdir -p "$OUT"

KOTLIN_COMPILER="$L/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar"
KOTLIN_STDLIB="$L/kotlin-stdlib-$KOTLIN_VERSION.jar"
COROUTINES="$L/kotlinx-coroutines-core-jvm-$COROUTINES_VERSION.jar"
ANNOTATIONS="$L/annotations-$ANNOTATIONS_VERSION.jar"
JUNIT_JAR="${JUNIT_JAR:-$L/junit-$JUNIT_VERSION.jar}"
HAMCREST_JAR="${HAMCREST_JAR:-$L/hamcrest-core-$HAMCREST_VERSION.jar}"

require_file "$KOTLIN_COMPILER"
require_file "$KOTLIN_STDLIB"
require_file "$COROUTINES"
require_file "$ANNOTATIONS"
require_file "$JUNIT_JAR"
require_file "$HAMCREST_JAR"

CP="$KOTLIN_STDLIB:$JUNIT_JAR:$HAMCREST_JAR:$COROUTINES:$ANNOTATIONS"
COMPILER_CP="$KOTLIN_COMPILER:$KOTLIN_STDLIB:$ANNOTATIONS:$COROUTINES"
# The compiler needs kotlin-reflect internally even with -no-reflect for the compiled program.
if [[ -f "$L/kotlin-reflect-$KOTLIN_VERSION.jar" ]]; then
  COMPILER_CP="$COMPILER_CP:$L/kotlin-reflect-$KOTLIN_VERSION.jar"
fi
# Older Kotlin compilers also need trove4j; include it when the distribution ships it.
if [[ -f "$L/trove4j-1.0.20200330.jar" ]]; then
  COMPILER_CP="$COMPILER_CP:$L/trove4j-1.0.20200330.jar"
fi

COMPILE_LOG="$OUT/compile.log"
if ! java -cp "$COMPILER_CP" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -cp "$CP" -d "$OUT" \
  $(find "$R/main/java/aktarjabed/com/jaascanner/core" -name "*.kt" ! -name ContextExt.kt ! -name TaskExt.kt) \
  "$R/main/java/aktarjabed/com/jaascanner/pdf/Shapes.kt" \
  "$R/main/java/aktarjabed/com/jaascanner/pdf/TextRunBuilder.kt" \
  "$HERE/ktest_stub/Stub.kt" "$HERE/ktest_stub/Stub2.kt" \
  $(find "$R/test" -name "*.kt") >"$COMPILE_LOG" 2>&1
then
  cat "$COMPILE_LOG" >&2
  echo "run_unit.sh: Kotlin compilation failed" >&2
  exit 1
fi
sed '/^Picked up/d' "$COMPILE_LOG" >&2

CLASSES="$(cd "$OUT" && find . -name "*Test.class" | sed 's#^\./##; s#\.class$##; s#/#.#g' | tr '\n' ' ')"
if [[ -z "${CLASSES// }" ]]; then
  echo "run_unit.sh: no test classes were produced" >&2
  exit 1
fi

java -cp "$OUT:$CP" org.junit.runner.JUnitCore $CLASSES 2>&1 \
  | sed '/^Picked up/d' \
  | tail -"${1:-25}"
