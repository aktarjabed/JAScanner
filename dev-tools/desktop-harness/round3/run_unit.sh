#!/bin/bash
# Compiles app/src/main/.../core (minus the two Android-only files) plus every unit test, against a
# plain Kotlin compiler, and runs them under JUnit 4 -- no Android Gradle plugin involved.
#
# usage: run_unit.sh [tailLines]
#
# Override these if your layout differs from the one this was run on:
#   JASCANNER_SRC - path to app/src (so .../main/java/... and .../test/... are under it)
#   GRADLE_LIB    - a Gradle distribution's lib/ folder (kotlin-compiler-embeddable 2.0.21,
#                   kotlin-stdlib, junit 4.13.2, hamcrest-core 1.3, kotlinx-coroutines-core-jvm,
#                   annotations)
#   WORK_DIR      - scratch directory for compiled classes (default: ./out)
set -e
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JASCANNER_SRC="${JASCANNER_SRC:-$HERE/../../../app/src}"
GRADLE_LIB="${GRADLE_LIB:-/opt/gradle-8.14.3/lib}"
WORK_DIR="${WORK_DIR:-$HERE/out}"

L="$GRADLE_LIB"; R="$JASCANNER_SRC"
OUT="$WORK_DIR"; rm -rf "$OUT"; mkdir -p "$OUT"
CP="$L/kotlin-stdlib-2.0.21.jar:$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar:$L/annotations-24.0.1.jar"
java -cp "$L/kotlin-compiler-embeddable-2.0.21.jar:$L/kotlin-stdlib-2.0.21.jar:$L/trove4j-1.0.20200330.jar:$L/annotations-24.0.1.jar:$L/kotlinx-coroutines-core-jvm-1.6.4.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -cp "$CP" -d "$OUT" \
  $(find "$R/main/java/aktarjabed/com/jaascanner/core" -name "*.kt" ! -name ContextExt.kt ! -name TaskExt.kt) \
  "$R/main/java/aktarjabed/com/jaascanner/pdf/Shapes.kt" \
  "$HERE/ktest_stub/Stub.kt" "$HERE/ktest_stub/Stub2.kt" \
  $(find "$R/test" -name "*.kt") 2>&1 | grep -v "^Picked up" || true
CLASSES=$(cd "$OUT" && find . -name "*Test.class" | sed 's#^\./##; s#\.class$##; s#/#.#g' | tr '\n' ' ')
java -cp "$OUT:$CP" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v "^Picked up" | tail -"${1:-25}"
