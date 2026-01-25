#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${HERE}/gcp-env.sh"

gcloud config set project "${PROJECT_ID}" >/dev/null

# Create Cloud SQL Postgres instance (public IP, Cloud SQL connector will be used by Cloud Run)
if ! gcloud sql instances describe "${DB_INSTANCE}" >/dev/null 2>&1; then
  gcloud sql instances create "${DB_INSTANCE}" \
    --database-version=POSTGRES_16 \
    --region="${REGION}" \
    --tier=db-f1-micro \
    --storage-type=SSD \
    --storage-size=10 \
    --backup-start-time=03:00 \
    --availability-type=ZONAL >/dev/null
fi

# Create DB
if ! gcloud sql databases describe "${DB_NAME}" --instance="${DB_INSTANCE}" >/dev/null 2>&1; then
  gcloud sql databases create "${DB_NAME}" --instance="${DB_INSTANCE}" >/dev/null
fi

# Create user
if ! gcloud sql users list --instance="${DB_INSTANCE}" --format='value(name)' | grep -qx "${DB_USER}"; then
  gcloud sql users create "${DB_USER}" --instance="${DB_INSTANCE}" --password="${DB_PASSWORD}" >/dev/null
else
  gcloud sql users set-password "${DB_USER}" --instance="${DB_INSTANCE}" --password="${DB_PASSWORD}" >/dev/null
fi

echo "OK: Cloud SQL ready"
