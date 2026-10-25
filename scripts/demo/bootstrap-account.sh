#!/bin/sh
set -eu

: "${DEMO_BASE_URL:?Set DEMO_BASE_URL to the approved HTTPS deployment URL}"
: "${DEMO_EMAIL:?Set DEMO_EMAIL for the controlled demo account}"
: "${DEMO_PASSWORD:?Set DEMO_PASSWORD; never commit it}"

case "$DEMO_BASE_URL" in
  https://*|http://localhost:*|http://127.0.0.1:*) ;;
  *) echo "DEMO_BASE_URL must use HTTPS (HTTP is allowed only for local verification)." >&2; exit 2 ;;
esac

cookie_file=$(mktemp)
response_file=$(mktemp)
trap 'rm -f "$cookie_file" "$response_file"' EXIT HUP INT TERM

csrf_json=$(curl --fail --silent --show-error --cookie "$cookie_file" --cookie-jar "$cookie_file" \
  "$DEMO_BASE_URL/api/auth/csrf")
csrf_header=$(printf '%s' "$csrf_json" | jq -r '.headerName')
csrf_token=$(printf '%s' "$csrf_json" | jq -r '.token')

status=$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
  --cookie "$cookie_file" --cookie-jar "$cookie_file" \
  --header 'Content-Type: application/json' --header "$csrf_header: $csrf_token" \
  --data "$(jq -n --arg email "$DEMO_EMAIL" --arg password "$DEMO_PASSWORD" '{email:$email,password:$password,displayName:"StockFlow Demo",storeName:"StockFlow Synthetic Demo",currencyCode:"USD",timeZone:"America/New_York"}')" \
  "$DEMO_BASE_URL/api/auth/register")

if [ "$status" != "201" ]; then
  echo "Demo registration failed with HTTP $status. Registration must be enabled only for this controlled bootstrap." >&2
  jq -c '{code,detail}' "$response_file" >&2 || true
  exit 1
fi

jq '{userId,storeId,email,role}' "$response_file"
echo "Record userId/storeId securely, immediately disable registration, then run the one-off synthetic seed task." >&2
