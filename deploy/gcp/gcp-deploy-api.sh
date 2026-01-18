#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"
# shellcheck disable=SC1091
source "${HERE}/gcp-env.sh"

gcloud config set project "${PROJECT_ID}" >/dev/null

INSTANCE_CONN_NAME="$(gcloud sql instances describe "${DB_INSTANCE}" --format='value(connectionName)')"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_API}:$(date +%Y%m%d-%H%M%S)"

# Build + push image via Cloud Build
(
  cd "${ROOT}"
  gcloud builds submit --tag "${IMAGE}" --timeout=1200s >/dev/null
)

# Deploy to Cloud Run
# Use Cloud SQL unix socket: jdbc:postgresql:///<db>?host=/cloudsql/<INSTANCE_CONN_NAME>
DB_URL="jdbc:postgresql:///${DB_NAME}?host=/cloudsql/${INSTANCE_CONN_NAME}"

gcloud run deploy "${SERVICE_API}" \
  --region="${REGION}" \
  --image="${IMAGE}" \
  --allow-unauthenticated \
  --add-cloudsql-instances="${INSTANCE_CONN_NAME}" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,QUANTOR_DB_URL=${DB_URL},QUANTOR_DB_USER=${DB_USER},QUANTOR_DB_PASSWORD=${DB_PASSWORD}" \
  --port=8080 >/dev/null

echo "OK: API deployed"
