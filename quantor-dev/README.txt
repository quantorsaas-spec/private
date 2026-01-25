Quantor dev scripts

Where to run

Option A (recommended): run from Quantor root:
  ~/projects/quantor/Quantor
  ./dev-up.sh

Option B: run from quantor-dev:
  ~/projects/quantor/Quantor/quantor-dev
  ./dev-up.sh

Make executable (if needed):
  cd ~/projects/quantor/Quantor/quantor-dev
  chmod +x dev-up.sh dev-down.sh dev-status.sh

Also (optional but useful):
  cd ~/projects/quantor/Quantor
  chmod +x dev-up.sh dev-down.sh dev-status.sh verify.sh

Start everything:
  ./dev-up.sh

Status:
  ./dev-status.sh

Stop everything:
  ./dev-down.sh

If verify.sh is not executable, you can always run:
  cd ~/projects/quantor/Quantor
  bash ./verify.sh

Notes:
- Root dir can be overridden:
  QUANTOR_ROOT=/path/to/Quantor ./dev-up.sh
- Postgres container name can be overridden:
  QUANTOR_POSTGRES_CONTAINER=my-postgres ./dev-up.sh
- Logs:
  /tmp/quantor-api.log
  /tmp/quantor-worker.log
