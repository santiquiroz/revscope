# Security Policy

## Supported versions

Only the **latest GitHub Release** of RevScope receives security fixes.

## Reporting a vulnerability

Please report vulnerabilities privately via GitHub’s **Security** tab → **Report a vulnerability**.

Do **not** open a public issue for security problems.

## Scope notes

When reporting, these areas are especially relevant:

- **Local-network MCP server** — writes that affect the vehicle ECU require a tap-to-confirm on the phone; report any bypass of that confirmation.
- **Bluetooth adapter pairing** — pairing, bonding, and reconnect flows.
- **Backup / restore files** — export and import of app data.
- **API keys** — keys stored with `EncryptedSharedPreferences`.
