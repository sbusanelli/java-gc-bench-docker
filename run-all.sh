#!/usr/bin/env bash
set -euo pipefail
docker compose build
mkdir -p logs
docker compose up --abort-on-container-exit
