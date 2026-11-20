#!/usr/bin/env bash
set -euo pipefail

repository=${GITHUB_REPOSITORY:-1-harshr/DesiCrics}

for workflow in server-image.yml android-release.yml pages.yml; do
  printf 'Starting %s on main\n' "$workflow"
  gh workflow run "$workflow" --repo "$repository" --ref main
done
