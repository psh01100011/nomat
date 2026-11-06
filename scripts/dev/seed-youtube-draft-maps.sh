#!/usr/bin/env bash

set -euo pipefail

LOGIN_ID="${1:-jason123}"
FIRST_QUESTION_COUNT="${2:-30}"
SECOND_QUESTION_COUNT="${3:-100}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_NAME="${DB_NAME:-nomat}"
DB_USER="${DB_USER:-nomat}"
DB_PASSWORD="${DB_PASSWORD:-nomat}"

if [[ ! "$LOGIN_ID" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "login id may contain only letters, numbers, and underscores" >&2
  exit 1
fi

for count in "$FIRST_QUESTION_COUNT" "$SECOND_QUESTION_COUNT"; do
  if [[ ! "$count" =~ ^[1-9][0-9]*$ ]] || (( 10#$count > 500 )); then
    echo "question count must be between 1 and 500" >&2
    exit 1
  fi
done

if [[ "$FIRST_QUESTION_COUNT" == "$SECOND_QUESTION_COUNT" ]]; then
  echo "the two question counts must be different" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/seed-youtube-draft-maps.sql"

{
  printf "SET @seed_login_id = '%s';\n" "$LOGIN_ID"
  printf "SET @seed_first_question_count = %s;\n" "$FIRST_QUESTION_COUNT"
  printf "SET @seed_second_question_count = %s;\n" "$SECOND_QUESTION_COUNT"
  printf "SOURCE %s;\n" "$SQL_FILE"
} | MYSQL_PWD="$DB_PASSWORD" mysql \
  --protocol=TCP \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  "$DB_NAME"
