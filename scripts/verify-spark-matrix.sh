#!/usr/bin/env bash
set -euo pipefail

profiles=(
  spark35-it
  spark35-scala213-it
  spark40-it
  spark41-it
  spark42-it
)

for profile in "${profiles[@]}"; do
  echo "==> Verifying ${profile}"
  mvn -B -P"${profile}" clean verify
 done
