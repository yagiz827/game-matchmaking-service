#!/usr/bin/env bash
# Plays one full tournament round against a running service:
# creates five players (one per country), levels them up, enters them, and prints the result.
# Usage: ./scripts/demo.sh [base-url]
set -euo pipefail

API="${1:-http://localhost:8080}/api/v1"
MIN_LEVEL=20

# Extracts a top-level field from a flat JSON object (no jq/python needed).
field() { grep -o "\"$1\":\"\?[^,\"}]*" | head -1 | sed 's/.*:"\?//'; }

tournament=$(curl -sf -X POST "$API/tournaments" | field id)
echo "Created tournament $tournament"

players=()
for country in TR US UK FR DE; do
  id=$(curl -sf -X POST "$API/players" -H 'Content-Type: application/json' \
    -d "{\"username\": \"demo-$country-$RANDOM\", \"country\": \"$country\"}" | field id)
  for _ in $(seq 2 "$MIN_LEVEL"); do
    curl -sf -X POST "$API/players/$id/level-up" > /dev/null
  done
  curl -sf -X POST "$API/tournaments/$tournament/entries" -H 'Content-Type: application/json' \
    -d "{\"playerId\": $id}" > /dev/null
  echo "Player $id ($country) joined the queue"
  players+=("$id")
done

echo "Waiting for the match to be played..."
for _ in $(seq 1 30); do
  status=$(curl -sf "$API/tournaments/$tournament/entries/${players[0]}" | field status)
  [ "$status" = "FINISHED" ] && break
  sleep 1
done

echo
echo "Leaderboard:"
curl -sf "$API/tournaments/$tournament/leaderboard" | sed 's/},{/},\n{/g'
echo
