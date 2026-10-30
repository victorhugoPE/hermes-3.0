#!/usr/bin/env bash
# Runner de consulta rápida do Hermes 3.0 RAG
set -euo pipefail

PYTHON_ENV="/Users/victorhugo/Downloads/BrProject-Native/.sentinel/venv/bin/python3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$PYTHON_ENV" "$SCRIPT_DIR/hermes_query.py" "$@"
