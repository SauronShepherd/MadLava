#!/usr/bin/env bash
set -euo pipefail

mvn -B clean verify
"$(dirname "$0")/verify-spark-matrix.sh"
