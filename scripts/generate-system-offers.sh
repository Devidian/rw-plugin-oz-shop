#!/usr/bin/env bash

set -euo pipefail

source_file="${1:-src/system-offers.complete.json}"
output_dir="${2:-target/generated-system-offers}"

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required to generate system offer files." >&2
    exit 1
fi

mkdir -p "$output_dir"
find "$output_dir" -maxdepth 1 -type f -name 'system-offers.*.json' -delete

classified_tier='^T([0-9]|1[01])$'

jq --arg tier_pattern "$classified_tier" \
    'map(select((._economyTier // "unclassified") | test($tier_pattern)) | select(.isEnabled == true))' \
    "$source_file" > "$output_dir/system-offers.default.json"

jq -r --arg tier_pattern "$classified_tier" \
    'map(select((._economyTier // "unclassified") | test($tier_pattern)) | ._economyTier) | unique[]' \
    "$source_file" |
while IFS= read -r tier; do
    if [[ "$tier" =~ ^T([0-9]+)$ ]]; then
        jq --arg tier "$tier" \
            'map(select((._economyTier // "unclassified") == $tier) | .isEnabled = true)' \
            "$source_file" > "$output_dir/system-offers.tier-${BASH_REMATCH[1]}.json"
    else
        echo "Unsupported economy tier: $tier" >&2
        exit 1
    fi
done
