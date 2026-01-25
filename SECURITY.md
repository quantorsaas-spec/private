# Security / Secrets

This repository must **never** contain real credentials.

## Rules
- Keep **all** secrets in environment variables or a secret manager.
- Do not commit: `.env`, `secrets.properties`, `secrets.enc`, `audit.log`, databases (`*.db`), build artifacts (`target/`).

## Local development
- Copy `.env.example` to `.env` and set values.
- Copy `config/config.properties.example` to `config/config.properties` **outside** git, or pass as env/volume.

## Production (GCP)
- Store secrets in **Secret Manager** and inject into Cloud Run.
