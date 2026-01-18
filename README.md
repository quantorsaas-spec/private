# Binance Java Bot (Spot) — EMA Cross

Полноценный учебный каркас бота для Binance Spot на Java 17 + Maven.
Есть режимы: **backtest**, **paper**, **live**. REST через OkHttp, JSON через Jackson, логирование SLF4J.

> ⚠️ Важно: это обучающий пример, не финансовый совет. Начинайте с **testnet**.

## 📦 Установка

1) Установите **Java 17+** и **Maven**.
2) Скачайте и распакуйте этот проект.
3) Скопируйте `config/config.properties.example` → `src/main/resources/config.properties` и заполните ключи:
   ```properties
   baseUrl=https://testnet.binance.vision   # для тестов
   apiKey=ВАШ_API_КЛЮЧ
   apiSecret=ВАШ_API_СЕКРЕТ
   symbol=BTCUSDT
   interval=1m
   fastEma=9
   slowEma=21
   positionUSDT=50
   feeRate=0.001
   stopLossPct=0.01
   takeProfitPct=0.02
   ```
4) Соберите jar:
   ```bash
   mvn -q -DskipTests package
   ```

## ▶️ Запуск

- Бэктест (история):
  ```bash
  java -jar target/binance-java-bot-1.0.0-jar-with-dependencies.jar backtest
  ```

- Бумажная торговля (симуляция):
  ```bash
  java -jar target/binance-java-bot-1.0.0-jar-with-dependencies.jar paper
  ```

- Live (осторожно, реальные ордера закомментированы; используйте testnet):
  ```bash
  java -jar target/binance-java-bot-1.0.0-jar-with-dependencies.jar live
  ```

## 🔐 Безопасность

- Не коммитьте реальные ключи в репозиторий.
- `config/config.properties` включён в `.gitignore`.
- Для продакшена можно использовать переменные окружения вместо файла.

## 🛠 Что внутри

- `core/` — модели свечей, стратегия EMA, риск-менеджмент, позиция, утилиты.
- `exchange/` — клиент Binance REST (подпись HMAC-SHA256), HTTP-обёртка.
- `engine/` — режимы backtest/paper/live.
- `App.java` — точка входа, выбор режима.

## 🔭 Идеи для улучшений

- WebSocket kline вместо опроса.
- Точное округление qty/цены по `exchangeInfo`.
- БД для истории и сделок.
- Телеграм-уведомления.
- Доп. стратегии (RSI/MACD/BB).

Удачи!

## CLI (enterprise)
```bash
java -jar quantor-cli.jar setup
java -jar quantor-cli.jar configure init
java -jar quantor-cli.jar configure list --all
java -jar quantor-cli.jar configure set BINANCE_API_KEY <value> --encrypt
java -jar quantor-cli.jar validate-config
java -jar quantor-cli.jar preflight
```


## Profiles (dev/prod)
You can keep multiple configs under `./config/<profile>/` and run tools with:

- `java -jar quantor-cli.jar preflight --profile dev`
- `java -jar quantor-cli.jar doctor --profile prod`

Copy examples from `config/dev/*.example` and `config/prod/*.example` into real files inside each profile folder.


## CI-ready

Examples:
```bash
java -jar quantor-cli.jar preflight --profile prod --account main --json --out reports/preflight.json --fail-on-warn
java -jar quantor-cli.jar doctor --profile prod --account main --json --out reports/doctor.json
java -jar quantor-cli.jar configure --profile prod --account main export --json --out reports/config.json
java -jar quantor-cli.jar setup --profile prod --account main --non-interactive --from-env
```


## Paper trading (multi-symbol)

```bash
# Runs 1 iteration over symbols (fetch candles, decide, buy/sell on paper)
java -jar quantor-cli.jar paper-portfolio BTCUSDT,ETHUSDT 1m 200 1
```

Paper config keys (config.properties):
- paper.quoteAsset=USDT
- paper.initialBalance.USDT=1000
- paper.feeBps=10
- paper.slippageBps=5

---

## Multi-exchange (direction)
### PAPER mode with real market data

You can run **paper execution** while streaming candles from a real exchange:

```properties
exchange=PAPER
paper.marketDataExchange=BINANCE   # or BYBIT
symbol=BTC/USDT
interval=1m
```


Quantor is moving to an **exchange-agnostic** architecture. Binance should be *one adapter*, not the core.

Current adapters:
- BINANCE (real orders on testnet methods by default)
- BYBIT (v5 spot: klines + market orders; set BYBIT_BASE_URL for testnet)
- PAPER (simulation; can use any adapter for candles)

In this zip, we introduced the first "fixed" contract in `quantor-application`:

- `com.quantor.application.exchange.ExchangePort`
- `ExchangeId`, `MarketSymbol`, `Timeframe`

Infrastructure can now provide adapters like:

- `UnifiedBinanceExchangeAdapter` (maps `MarketSymbol` + `Timeframe` to Binance formats)
- `PaperExchangeAdapter` (placeholder for the next step)

Next logical step: implement `PaperExchangeAdapter` properly and migrate engines/use-cases to use `ExchangePort`.
