#!/usr/bin/env bash
# Builds app/src/main/assets/privacy/consent.js.
# Run by hand only (see README.md). Never from Gradle or CI.
set -euo pipefail
cd "$(dirname "$0")"
npm ci --no-audit --no-fund
node trim-rules.mjs
VERSION=$(node -p "JSON.parse(require('fs').readFileSync('node_modules/@duckduckgo/autoconsent/package.json','utf8')).version")
npx esbuild entry.ts --bundle --minify --format=iife --target=chrome74 --legal-comments=none \
    --outfile=build/consent.min.js --log-level=warning
OUT=../../app/src/main/assets/privacy/consent.js
mkdir -p "$(dirname "$OUT")"
{
    printf '/*! ThirtySix cookie-banner auto-decline.\n'
    printf ' * Contains @duckduckgo/autoconsent %s, (c) DuckDuckGo, Inc., licensed under the\n' "$VERSION"
    printf ' * Mozilla Public License 2.0 (https://mozilla.org/MPL/2.0/).\n'
    printf ' * Source: https://github.com/duckduckgo/autoconsent/tree/v%s\n' "$VERSION"
    printf ' * Built with tools/privacy-scripts/ (generic and frame rules only, opt-out only). */\n'
    printf '(function(cfg){'
    cat build/consent.min.js
    printf '})\n'
} > "$OUT"
RAW=$(wc -c < "$OUT" | tr -d ' ')
GZ=$(gzip -9 -c "$OUT" | wc -c | tr -d ' ')
echo "consent.js: ${RAW} B raw, ${GZ} B gzip"
