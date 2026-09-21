#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
# Test observable workflow contracts and execute shell steps with offline tool doubles.
python3 -m unittest discover -s scripts/quality -p 'test_*.py'
echo "[ci-workflow-quality] verification passed."
