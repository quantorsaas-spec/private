#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"
# shellcheck disable=SC1091
source "${HERE}/gcp-env.sh"

# Defensive: avoid "--project \"\"" mistakes.
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID is empty. Set PROJECT_ID in deploy/gcp/gcp-env.sh or run: gcloud config set project <...>" >&2
  exit 1
fi

gcloud config set project "${PROJECT_ID}" >/dev/null

RUN_SA="${RUN_SA:-quantor-run@${PROJECT_ID}.iam.gserviceaccount.com}"

INSTANCE_CONN_NAME="$(gcloud sql instances describe "${DB_INSTANCE}" --format='value(connectionName)')"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_API}:$(date +%Y%m%d-%H%M%S)"

# Build + push image via Cloud Build
(
  cd "${ROOT}"
  gcloud builds submit --tag "${IMAGE}" --timeout=1200s >/dev/null
)

# Deploy to Cloud Run
# Cloud SQL connector (no IP allowlists). Requires dependency:
#   com.google.cloud.sql:postgres-socket-factory
DB_URL="jdbc:postgresql:///${DB_NAME}?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=${INSTANCE_CONN_NAME}"

gcloud run deploy "${SERVICE_API}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --image="${IMAGE}" \
  --allow-unauthenticated \
  --service-account="${RUN_SA}" \
  --add-cloudsql-instances="${INSTANCE_CONN_NAME}" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,QUANTOR_DB_URL=${DB_URL},QUANTOR_DB_USER=${DB_USER},QUANTOR_TRACING_ENABLED=${QUANTOR_TRACING_ENABLED:-false}" \
  --set-secrets="QUANTOR_DB_PASSWORD=QUANTOR_DB_PASSWORD:latest,QUANTOR_MASTER_PASSWORD=QUANTOR_MASTER_PASSWORD:latest,QUANTOR_JWT_SECRET=QUANTOR_JWT_SECRET:latest,QUANTOR_LEMONSQUEEZY_WEBHOOK_SECRET=QUANTOR_LEMONSQUEEZY_WEBHOOK_SECRET:latest" \
  --port=8080 >/dev/null

echo "OK: API deployed"
