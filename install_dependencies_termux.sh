#!/bin/bash
set -e

echo "=== Installing Termux build dependencies ==="
pkg update -y
pkg install -y openjdk-17 aapt aapt2 apksigner zip termux-api kotlin maven d8 python libxml2-utils

# Optional: only needed for instrumentation tests (./test-shizuku.sh); not available in every repo.
pkg install -y termux-shizuku-tools || echo "WARNING: termux-shizuku-tools unavailable, skipping (only needed for ./test-shizuku.sh)"

echo "=== Termux Build Dependencies Installed Successfully ==="