#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"
# shellcheck disable=SC1091
source "${HERE}/gcp-env.sh"

gcloud config set project "${PROJECT_ID}" >/dev/null

INSTANCE_CONN_NAME="$(gcloud sql instances describe "${DB_INSTANCE}" --format='value(connectionName)')"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_WORKER}:$(date +%Y%m%d-%H%M%S)"

(
  cd "${ROOT}"
  gcloud builds submit --tag "${IMAGE}" --timeout=1200s >/dev/null
)

DB_URL="jdbc:postgresql:///${DB_NAME}?host=/cloudsql/${INSTANCE_CONN_NAME}"

gcloud run deploy "${SERVICE_WORKER}" \
  --region="${REGION}" \
  --image="${IMAGE}" \
  --no-allow-unauthenticated \
  --add-cloudsql-instances="${INSTANCE_CONN_NAME}" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=gcp,QUANTOR_DB_URL=${DB_URL},QUANTOR_DB_USER=${DB_USER},QUANTOR_DB_PASSWORD=${DB_PASSWORD}" >/dev/null

echo "OK: Worker deployed"
