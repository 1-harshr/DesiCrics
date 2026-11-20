#!/usr/bin/env bash
set -euo pipefail

for workflow in server-image.yml android-release.yml pages.yml; do
  printf 'Starting %s on main\n' "$workflow"
  gh workflow run "$workflow" --repo 1-harshr/DesiCrics --ref main
done
