# Contributing to RevScope

## Ways to contribute

- **Vehicle PID tables** — proprietary Mode 22 PIDs for specific makes (Mazda, Renault, Nissan, TVS, etc.)
- **Motorcycle testing** — OBD protocol verification on bikes with ELM327
- **Bug reports** — adapter compatibility issues, parsing errors, crashes
- **UI components** — Compose gauges, chart types, themes
- **iOS port** — same ELM327 protocol over BLE (IOS-VLINK mode for Vgate)
- **Translations** — DTC descriptions in additional languages

## Development setup

```bash
git clone https://github.com/santiqupgui/revscope.git
cd revscope
# Open in Android Studio Hedgehog or later
# Min SDK 26, Target SDK 35
./gradlew test        # unit tests
./gradlew lint        # lint
./gradlew assembleDebug
```

## Code style

- Kotlin only
- Jetpack Compose for all UI
- Follow existing MVVM + Clean Architecture layers
- No function longer than 40 lines
- No file longer than 400 lines — extract modules
- Unit test new business logic in `core/obd/`

## Pull request process

1. Fork and create a branch from `main`
2. Add/update tests for any logic changes
3. Ensure `./gradlew test lint` passes
4. Reference the vehicle or adapter you tested against in the PR description
5. PRs without test coverage for new logic will be asked to add tests before merge

## Adding vehicle-specific PIDs

Edit `core/obd/src/main/assets/pids_proprietary.json`:

```json
{
  "make": "Mazda",
  "model": "CX-30",
  "year_range": "2020-2025",
  "protocol": "ISO15765-4",
  "mode": "22",
  "pids": [
    {
      "pid": "0C",
      "name": "RPM (Mazda)",
      "formula": "((A*256)+B)/4",
      "unit": "rpm",
      "min": 0,
      "max": 8000
    }
  ]
}
```

Include the vehicle you tested on and the adapter used.

## License

By contributing, you agree your contributions are licensed under Apache 2.0.
