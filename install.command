#!/bin/bash
# Подвійний клік на Mac: відкриває Термінал і запускає інсталятор Sun Home.
cd "$(dirname "$0")" || exit 1
if ! command -v python3 >/dev/null 2>&1; then
  echo "Потрібен Python 3: https://www.python.org/downloads/ (або xcode-select --install)."
  read -r -p "Enter — закрити"; exit 1
fi
python3 install.py "$@"
read -r -p "Enter — закрити"
