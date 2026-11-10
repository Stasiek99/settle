# Settle frontend

Angular 21 (standalone components, zoneless, signals) with PrimeNG 21 and the Aura theme.

## Running it

The API must be running on port 8080 (see the root README). Then:

```bash
npm ci
npm start
```

Open <http://localhost:4200>. The dev server proxies `/api/*` to `http://localhost:8080/*` (see `proxy.conf.json`), so the browser only talks to one origin and the API needs no CORS setup.

## Other commands

```bash
npm test          # unit tests (Vitest), watch mode
npm run build     # production build into dist/frontend
```

## How it is organised

- `src/app/core`: API base URL, models, error messages, auth (token store, service, interceptor, guards) and API clients.
- `src/app/features`: one folder per screen (`auth`, `groups`, `shell`), loaded lazily by the router.
- The access token lives in `sessionStorage`, so it disappears when the tab closes. It is readable by scripts on the page, which is why the app avoids rendering untrusted HTML; the long-term fix is an httpOnly cookie, which needs a backend change.

## PrimeNG licensing

PrimeNG 22 and `@primeuix/themes` 3 moved to the commercial PrimeUI license (a license key, and a notice without one). This project stays on the MIT-licensed lines, pinned on purpose:

- `primeng` `~21.1.10`
- `@primeuix/themes` `~2.0.3`

Do not bump either across a major version without checking the license first.
