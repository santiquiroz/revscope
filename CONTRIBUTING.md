# Contributing to RevScope

Thanks for helping improve RevScope.

## Ways to contribute

- **Vehicle PID tables** — proprietary Mode 22 PIDs for specific makes (Mazda, Renault, Nissan, TVS, etc.)
- **Motorcycle testing** — OBD protocol verification on bikes with ELM327
- **Bug reports** — adapter compatibility issues, parsing errors, crashes
- **UI components** — Compose gauges, chart types, themes
- **iOS port** — same ELM327 protocol over BLE (IOS-VLINK mode for Vgate)
- **Translations** — UI strings and DTC guides in additional languages

## Development setup

```bash
git clone https://github.com/santiquiroz/revscope.git
cd revscope
# Open in Android Studio Hedgehog or later
# JDK 17 · Min SDK 26 · Target SDK 35
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew verifyRoborazziDebug
```

## Code style

- Kotlin only
- Jetpack Compose for all UI
- Follow existing MVVM + Clean Architecture layers
- No function longer than 40 lines
- No file longer than 400 lines — extract modules
- Unit test new business logic in `core/obd/`

## Pull request process

1. Fork and create a branch from `master`
2. Add/update tests for any logic changes
3. Ensure `./gradlew testDebugUnitTest` passes
4. Reference the vehicle or adapter you tested against in the PR description
5. PRs without test coverage for new logic will be asked to add tests before merge

## Adding vehicle-specific PIDs and CAN profiles

- **Mode 01 PIDs** live in `core/obd/src/main/assets/pids_mode01.json`. Manufacturer PIDs use the same schema: write them in **Settings → Custom PIDs**, test them on your vehicle, then use **Share pack** and attach the JSON to an issue or PR. See [Custom PIDs](docs/en/configuration.md) in the configuration guide.
- **Manufacturer readings (Mode 22 / UDS)** go in `core/obd/src/main/assets/catalogo_propietario.json` (derived from [OBDb](https://github.com/OBDb), CC-BY-SA-4.0). Mark entries `verificado: true` only after testing them on a real vehicle.
- **CAN bus profiles** (gear, side stand, clutch, kill switch read from the bus) live in `core/obd/src/main/assets/perfiles_can/`, one JSON per vehicle. `tvs.apache_rtr_160_4v.2024.json` is the reference.

Always include the vehicle (make, model, year) and adapter you tested with.

## Translating RevScope

English is the source language in each module’s `src/main/res/values/strings.xml`. Spanish lives in `values-es/`.

To add a language:

1. Create `src/main/res/values-<lang>/strings.xml` in every module that already has a `values/strings.xml`
2. Add `core/obd/src/main/assets/taller/dtc_guide_<lang>.json`
3. Run `python scripts/i18n/parity.py app core/* feature/* wear` before opening the PR

Keys are prefixed per module. Keep format arguments (`%1$s`, etc.) identical to the English source.

Thank you to [@access201010](https://github.com/access201010) for the first English translation (their fork’s `english-uk` branch).

## Commit messages

Maintainers write commit messages in Spanish. Contributors may write them in English.

## License

By contributing, you agree your contributions are licensed under Apache 2.0.
