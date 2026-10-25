#!/bin/sh
set -eu

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:=5432}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${DB_SSL_MODE:=require}"
: "${DEMO_STORE_ID:?DEMO_STORE_ID is required}"
: "${DEMO_USER_ID:?DEMO_USER_ID is required}"

if [ "$DEMO_STORE_ID" = "REQUIRED_OVERRIDE" ] || [ "$DEMO_USER_ID" = "REQUIRED_OVERRIDE" ]; then
  echo "Demo identifiers must be supplied as ECS task overrides." >&2
  exit 2
fi

export PGPASSWORD="$POSTGRES_PASSWORD"
exec psql \
  "host=$DB_HOST port=$DB_PORT dbname=$POSTGRES_DB user=$POSTGRES_USER sslmode=$DB_SSL_MODE" \
  --set=store_id="$DEMO_STORE_ID" \
  --set=user_id="$DEMO_USER_ID" \
  --file=/seed/analytics-demo.sql
