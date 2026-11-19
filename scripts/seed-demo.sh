#!/usr/bin/env bash
# Fills the local database with a demo season of Pune club cricket (see server/.../seed/DemoSeed.kt).
#
#   scripts/seed-demo.sh                 seed, without putting anyone's account in the demo
#   scripts/seed-demo.sh <uid>           also put this signed-in account in Kothrud Kings
#   scripts/seed-demo.sh --fresh [uid]   wipe the local database first (everything in it is lost)
#
# Your uid is the Firebase UID of the account you signed in with; the script lists the ones in the database.
set -euo pipefail

cd "$(dirname "$0")/.."

fresh=false
uid=""
for arg in "$@"; do
  case "$arg" in
    --fresh) fresh=true ;;
    -h|--help) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) uid="$arg" ;;
  esac
done

psql() { docker compose exec -T postgres psql -U desicrics -d desicrics "$@"; }

# The seeder's own output, without the server's debug logging (warnings and errors still show).
seed() { ./gradlew -q --console=plain :server:seedDemo "$@" 2>&1 | grep -Ev '^[0-9-]+ [0-9:.]+ \[.*\] (TRACE|DEBUG|INFO) |^w: '; }

if ! docker info >/dev/null 2>&1; then
  echo "Docker isn't running. Start Docker Desktop (or your Docker runtime) and try again." >&2
  exit 1
fi

if $fresh; then
  read -r -p "This deletes everything in the local database. Continue? [y/N] " answer
  [[ "$answer" == [yY] ]] || { echo "Cancelled."; exit 1; }
  docker compose down -v
fi

echo "Starting Postgres..."
docker compose up -d --wait postgres

if $fresh && [[ -n "$uid" ]]; then
  echo "Note: a wiped database has no accounts. Sign in to the app once (with the server running), then run:"
  echo "  scripts/seed-demo.sh $uid"
  uid=""
fi

if [[ -z "$uid" ]]; then
  # Signed-up accounts, so you can pick yours next time. Fails quietly before the first server start.
  accounts=$(psql -tA -F ' | ' -c "select uid, name from users where uid not like 'demo-%' order by created_at" 2>/dev/null || true)
  if [[ -n "$accounts" ]]; then
    echo "Accounts in the database (pass a uid to join the demo as that person):"
    echo "$accounts" | sed 's/^/  /'
  fi
  seed
else
  seed --args="--for $uid"
fi

echo
echo "Next: start the server with ./gradlew :server:run and run adb reverse tcp:8080 tcp:8080 for the app."
