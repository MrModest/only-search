#!/usr/bin/env bash
set -euo pipefail
git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
# A rerun resumes its own release, even when main has advanced.
existing=$(git tag --points-at "$GITHUB_SHA" --list 'v*' | head -1)
if [[ -z "$existing" ]]; then
  prior=$(git log origin/main --format=%H --fixed-strings --grep="Release-Source: $GITHUB_SHA" | head -1)
  if [[ -n "$prior" ]]; then existing=$(git tag --points-at "$prior" --list 'v*' | head -1); fi
fi
subject=$(git show -s --format=%s "$GITHUB_SHA")
if [[ "$subject" == chore\(release\):\ v* ]]; then
  existing=${subject#chore(release): }
fi
if [[ -n "$existing" ]]; then
  git rev-parse "$existing"
  echo "tag=$existing" >> "$GITHUB_OUTPUT"
  exit 0
fi
kind=${MANUAL_BUMP:-$(python3 .github/scripts/subject.py classify "$subject")}
[[ "$kind" != none ]] || exit 0
# Recompute the bump after rebasing on a concurrently updated main.
for attempt in {1..10}; do
  git fetch origin main --tags
  git checkout -B release-work origin/main
  version=$(python3 .github/scripts/subject.py bump "$(cat version.txt)" "$kind")
  echo "$version" > version.txt
  git add version.txt
  git commit -m "chore(release): v$version" -m "Release-Source: $GITHUB_SHA"
  git tag "v$version"
  if git push --atomic origin HEAD:main "refs/tags/v$version"; then
    echo "tag=v$version" >> "$GITHUB_OUTPUT"
    exit 0
  fi
  git tag -d "v$version"
done
exit 1
