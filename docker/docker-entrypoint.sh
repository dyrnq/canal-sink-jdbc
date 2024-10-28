#!/usr/bin/env bash
set -eo pipefail

_main() {
    FULL_JAR="${FULL_JAR:-/app/canal-sink-jdbc.jar}"
    exec java $JAVA_OPTS -jar "${FULL_JAR}" "$@"
}

_main "$@"