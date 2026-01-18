How to configure Quantor (product mode)

Fast way (recommended):
1) Run setup wizard:
   java -jar quantor-cli.jar setup

It will:
- create ./config folder if missing
- create config.properties + secrets.properties
- ask for required keys
- optionally encrypt secrets as ENC(...)
- validate config

Files:
- config.properties          (non-secrets)
- secrets.properties         (secrets; DO NOT COMMIT)
- .env                       (optional overrides, useful for local/dev)

Encryption:
- Secrets can be stored as ENC(base64...)
- To run Quantor with encrypted secrets, set master password:
    QUANTOR_MASTER_PASSWORD=your_password
  or:
    -Dquantor.masterPassword=your_password

Environment overrides:
- Any key can be overridden via OS env vars.
- You can also use QUANTOR_* format:
    QUANTOR_BINANCE_API_KEY=...
    QUANTOR_BINANCE_API_SECRET=...

    # Bybit (v5)
    QUANTOR_BYBIT_API_KEY=...
    QUANTOR_BYBIT_API_SECRET=...
    # Optional:
    # QUANTOR_BYBIT_BASE_URL=https://api-testnet.bybit.com
    # QUANTOR_BYBIT_RECV_WINDOW=5000
    QUANTOR_TELEGRAM_BOT_TOKEN=...
    QUANTOR_TELEGRAM_CHAT_ID=...
    QUANTOR_CHATGPT_API_KEY=...
    QUANTOR_LOG_LEVEL=INFO

Validation:
- java -jar quantor-cli.jar validate-config

Safety:
- secrets.properties must be in .gitignore


Enterprise config commands:
- Generate templates:   java -jar quantor-cli.jar configure init
- List keys:           java -jar quantor-cli.jar configure list --all
- Set encrypted secret: java -jar quantor-cli.jar configure set BINANCE_API_KEY <value> --encrypt
- Preflight checks:    java -jar quantor-cli.jar preflight
