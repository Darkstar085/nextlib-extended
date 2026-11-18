#!/usr/bin/env bash
set -euo pipefail

REPO="$GITHUB_REPOSITORY"

echo "=== Cleaning up stale GitHub Actions resources for ${REPO} ==="

# 1. Stale Workflow Runs (keep only the latest run for each workflow)
runs_json="$(
  gh api --paginate "/repos/${REPO}/actions/runs?per_page=100" |
    jq -s '[.[] | .workflow_runs[]?]'
)"

# Keep the latest run ID per workflow
latest_run_ids="$(
  echo "$runs_json" |
    jq -r '
      group_by(.workflow_id)
      | .[]
      | sort_by(.created_at)
      | reverse
      | .[0].id
    '
)"

# Delete older run IDs
echo "$runs_json" |
  jq -r '
    group_by(.workflow_id)
    | .[]
    | sort_by(.created_at)
    | reverse
    | .[1:][].id
  ' |
  while read -r id; do
    if [[ -n "$id" ]]; then
      echo "Deleting stale workflow run: $id"
      gh api --method DELETE "/repos/${REPO}/actions/runs/${id}" >/dev/null || true
    fi
  done

# 2. Artifacts (delete unexpired artifacts not belonging to the latest runs)
keep_runs_csv="$(echo "$latest_run_ids" | paste -sd, -)"

gh api --paginate "/repos/${REPO}/actions/artifacts?per_page=100" |
  jq -s --arg latest "$keep_runs_csv" '
    ($latest | split(",") | map(select(length > 0) | tonumber)) as $keep
    | [.[] | .artifacts[]?]
    | .[]
    | select(.expired == false)
    | select((.workflow_run.id // 0) as $run | ($keep | index($run) | not))
    | .id
  ' |
  while read -r id; do
    if [[ -n "$id" ]]; then
      echo "Deleting stale artifact: $id"
      gh api --method DELETE "/repos/${REPO}/actions/artifacts/${id}" >/dev/null || true
    fi
  done

# 3. Stale Caches (group by normalized key without commit hash, keep newest cache per key)
gh api --paginate "/repos/${REPO}/actions/caches?per_page=100" |
  jq -s -r '
    [.[] | .actions_caches[]?]
    | map(. + {normalized_key: (.key | sub("-[0-9a-fA-F]{32,64}$"; ""))})
    | group_by(.normalized_key)
    | .[]
    | sort_by(.created_at)
    | reverse
    | .[1:][].id
  ' |
  while read -r id; do
    if [[ -n "$id" ]]; then
      echo "Deleting stale cache: $id"
      gh api --method DELETE "/repos/${REPO}/actions/caches/${id}" >/dev/null || true
    fi
  done

echo "Cleanup complete."
