# Errors: an HTTPS app configured for davidharris.net, running on localhost

## Scenario

Both projects are configured to run on `https://davidharris.net`. They run on a machine that only knows itself as `localhost`. Nothing maps `davidharris.net` in `/etc/hosts`, and the name does not resolve on this machine.

| Piece | Configuration |
|---|---|
| Certificate | Self-signed, CN and SAN `davidharris.net` only, in `certs/davidharris.net.crt` and `certs/davidharris.net.key` |
| Backend (`preferences-spring/preferences`) | Spring Boot, HTTPS only on port 8080, using that certificate. Swagger's OpenAPI server is `https://davidharris.net:8080` |
| Frontend (`preferences-angular/user-app`) | `ng serve` over HTTPS on `localhost:8445` with the same certificate. The dev proxy sends `/users` to `https://davidharris.net:8080` with certificate checks on (`secure: true`) |
| Angular tests | Before each test, a real `fetch` to `https://davidharris.net:8080`, with `NODE_EXTRA_CA_CERTS` set to the certificate |

Recorded on 2026-11-20. Both servers were started exactly as configured: `./gradlew bootRun` and `npm start`. They were probed with curl. "Trusting the certificate" means `curl --cacert certs/davidharris.net.crt`.

## Backend (port 8080)

The server starts normally: `Tomcat started on port 8080 (https)`.

| # | Request | Error |
|---|---|---|
| B1 | `https://davidharris.net:8080`, the configured URL | `curl: (6) Could not resolve host: davidharris.net` |
| B2 | `https://localhost:8080`, without trusting the certificate | `curl: (60) SSL certificate problem: self-signed certificate` |
| B3 | `https://localhost:8080`, trusting the certificate | `curl: (60) SSL: no alternative certificate subject name matches target hostname 'localhost'` |
| B4 | `http://localhost:8080`, plain HTTP | `Bad Request. This combination of host and port requires TLS.` |
| B5 | Swagger UI at `https://localhost:8080/swagger-ui.html`, certificate checks off (`-k`) | The page loads (HTTP 200), but the OpenAPI document lists `"servers":[{"url":"https://davidharris.net:8080"}]`. Every "Try it out" request fails as in B1 |

## Frontend (port 8445)

The dev server starts and lists `https://localhost:8445/` and `https://davidharris.net:8445/`. Only the first address can be reached.

| # | Request | Error |
|---|---|---|
| F1 | `https://davidharris.net:8445`, the configured URL | `curl: (6) Could not resolve host: davidharris.net` |
| F2 | `https://localhost:8445`, without trusting the certificate | `curl: (60) SSL certificate problem: self-signed certificate` |
| F3 | `https://localhost:8445`, trusting the certificate | `curl: (60) SSL: no alternative certificate subject name matches target hostname 'localhost'` |
| F4 | Load or Save in the app (`/users/1` through the dev proxy) | The app gets **HTTP 502**. The `ng serve` log shows `[vite] http proxy error: /users/1` and `Error: getaddrinfo ENOTFOUND davidharris.net` |
| F5 | `ng serve --host davidharris.net`, binding to the configured name | The dev server does not start: `Error: getaddrinfo ENOTFOUND davidharris.net` |

## Tests

| # | Run | Result |
|---|---|---|
| T1 | Angular tests (`npm test -- --watch=false`) | All 7 fail: `HTTPS handshake with https://davidharris.net:8080 failed: ENOTFOUND: getaddrinfo ENOTFOUND davidharris.net` |

The Spring project no longer has tests.

## Browsers

These were not run in a browser. For the certificate name mismatch (B3, F3), browsers typically show:

- Chrome: `NET::ERR_CERT_COMMON_NAME_INVALID`
- Firefox: `SSL_ERROR_BAD_CERT_DOMAIN`

## The three kinds of error

1. **The name doesn't resolve** (B1, B5, F1, F4, F5, T1). Anything that uses `davidharris.net` fails at the DNS lookup, before TLS starts.
2. **The certificate's name doesn't match** (B3, F3). The certificate is trusted, but it was issued for `davidharris.net`, and the server is reached as `localhost`.
3. **The certificate is untrusted** (B2, F2). It is self-signed, so the connection is rejected before the name is checked.

## What would make it work

None of these changes were made.

- Map the name to this machine in `/etc/hosts`: both `127.0.0.1 davidharris.net` and `::1 davidharris.net`. `ng serve` on `localhost` listens only on `[::1]`.
- Trust the certificate in the system store (on Fedora: `sudo trust anchor --store certs/davidharris.net.crt`). Firefox may also need it imported directly.
- Start `ng serve` with `NODE_EXTRA_CA_CERTS=../../certs/davidharris.net.crt`. Node doesn't use the system store, and the proxy checks certificates.
- Add `davidharris.net` to `allowedHosts` in `angular.json`, so the dev server accepts that host.
