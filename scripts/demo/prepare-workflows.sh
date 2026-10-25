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
body_file=$(mktemp)
trap 'rm -f "$cookie_file" "$body_file"' EXIT HUP INT TERM

csrf() {
  csrf_json=$(curl --fail --silent --show-error --cookie "$cookie_file" --cookie-jar "$cookie_file" \
    "$DEMO_BASE_URL/api/auth/csrf")
  csrf_header=$(printf '%s' "$csrf_json" | jq -r '.headerName')
  csrf_token=$(printf '%s' "$csrf_json" | jq -r '.token')
}

post_json() {
  path=$1
  payload=$2
  idempotency_key=${3:-}
  csrf
  if [ -n "$idempotency_key" ]; then
    status=$(curl --silent --show-error --output "$body_file" --write-out '%{http_code}' \
      --cookie "$cookie_file" --cookie-jar "$cookie_file" \
      --header 'Content-Type: application/json' --header "$csrf_header: $csrf_token" \
      --header "Idempotency-Key: $idempotency_key" --data "$payload" "$DEMO_BASE_URL$path")
  else
    status=$(curl --silent --show-error --output "$body_file" --write-out '%{http_code}' \
      --cookie "$cookie_file" --cookie-jar "$cookie_file" \
      --header 'Content-Type: application/json' --header "$csrf_header: $csrf_token" \
      --data "$payload" "$DEMO_BASE_URL$path")
  fi
  case "$status" in
    200|201|204) ;;
    *)
      echo "POST $path failed with HTTP $status" >&2
      jq -c '{code,detail}' "$body_file" >&2 || true
      exit 1
      ;;
  esac
}

post_json '/api/auth/login' "$(jq -n --arg email "$DEMO_EMAIL" --arg password "$DEMO_PASSWORD" \
  '{email:$email,password:$password}')"

post_json '/api/ai/query' '{"question":"Show revenue for the last 30 days"}'

post_json '/api/business-analysis/reports' \
  '{"question":"Which high-selling products are currently at risk of running out?","analysisType":"INVENTORY_RISK","preset":"LAST_30_DAYS","scenarios":[]}' \
  'portfolio-demo-inventory-report-v1'
report_id=$(jq -r '.report.reportId' "$body_file")
recommendation_id=$(jq -r '
  [.report.evidence[]
    | select(.source == "INVENTORY_SNAPSHOT")
    | select(.metricKey | startswith("inventory.available."))
    | .id] as $inventoryEvidence
  | .report.recommendations[]
  | select(.approvalRequired == true)
  | .evidenceIds as $references
  | select(any($references[]; . as $reference | $inventoryEvidence | index($reference)))
  | .id
' "$body_file" | head -1)

if [ -z "$report_id" ] || [ "$report_id" = "null" ] || [ -z "$recommendation_id" ]; then
  echo "The deterministic inventory report did not expose an actionable recommendation." >&2
  exit 1
fi

proposal_payload=$(jq -n --arg report "$report_id" --arg recommendation "$recommendation_id" \
  '{question:"Prepare a safe replenishment proposal for each verified at-risk product.",sourceReportId:$report,recommendationId:$recommendation}')
post_json '/api/restock-proposals/generate' "$proposal_payload" 'portfolio-demo-restock-proposal-v1'

jq --arg reportId "$report_id" '{reportId:$reportId,proposals:[.items[] | {id,sku,productName,status,proposedQuantity}]}' "$body_file"
echo "Synthetic AI conversation, evidence-backed report, and unapproved proposals are ready. Leave approval and receipt for the live walkthrough." >&2
