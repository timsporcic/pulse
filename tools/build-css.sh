#!/usr/bin/env bash
# Rebuilds src/main/resources/public/tailwind.css from the templates/HTML.
#
# The built CSS is committed (sovereignty: no CDN, no build step at runtime).
# Run this only when Tailwind classes change, then commit the output.
#
# Downloads the Tailwind standalone CLI on first use (dev-time only).
set -euo pipefail
cd "$(dirname "$0")/.."

TAILWIND_VERSION=4.3.3
CLI=tools/tailwind/tailwindcss-cli

if [ ! -x "$CLI" ]; then
  case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) suffix=macos-arm64 ;;
    Darwin-x86_64) suffix=macos-x64 ;;
    Linux-x86_64) suffix=linux-x64 ;;
    Linux-aarch64) suffix=linux-arm64 ;;
    *) echo "unsupported platform: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
  esac
  curl -sL -o "$CLI" \
    "https://github.com/tailwindlabs/tailwindcss/releases/download/v${TAILWIND_VERSION}/tailwindcss-${suffix}"
  chmod +x "$CLI"
fi

"$CLI" --input tools/tailwind/input.css --output src/main/resources/public/tailwind.css --minify
echo "wrote src/main/resources/public/tailwind.css"
