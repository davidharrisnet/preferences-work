# Running on localhost without the davidharris.net certificates

The two projects are configured for production: they run on `https://davidharris.net`, with the mkcert certificate, on a machine whose `/etc/hosts` points that name at itself. That configuration is what gets committed, and it must stay that way.

This guide is for developing on a machine **without** those certificates and **without** the hosts entry, on plain-HTTP `localhost`.

**The rule: don't change any file in the repo.** Nothing shows up in `git status`, so there's nothing to forget before a commit. If you ever have to change a file to get work done, see [section 6](#6-if-you-must-change-a-file-keep-it-out-of-commits).

### At a glance

| Project | Files changed in the repo | What you do instead |
|---|---|---|
| Spring Boot (`preferences-spring/preferences`) | **none** | start it with three environment variables ([4.1](#41-backend)) |
| Angular (`preferences-angular/user-app`) | **none** | start it with four flags ([4.2](#42-frontend)). One flag points to a proxy file that you create once, **outside the repo**, in `~/.config/preferences-dev/` ([section 3](#3-one-time-setup-a-proxy-file-outside-the-repo)) |

The proxy file is the only new file, and it belongs to the Angular dev server, not to Spring Boot. Spring Boot never reads it.

| | Production (committed) | Localhost (this guide) |
|---|---|---|
| Backend | `https://davidharris.net:8080` | `http://localhost:8080` |
| Frontend | `https://davidharris.net:8445` | `http://localhost:4200` |
| Needs the certificate, the mkcert CA and `/etc/hosts` | yes | no |

> Use `/usr/bin/curl` for the checks. Anaconda's `curl` comes first in your `PATH` and behaves differently with certificates.

---

## 1. What the committed configuration expects

These are the settings that tie the projects to davidharris.net. The localhost run overrides each one at start-up; none is edited.

| Where | Setting | Production value | Localhost override |
|---|---|---|---|
| `preferences-spring/preferences/src/main/resources/application.properties` | `server.ssl.enabled` | `true` | `SERVER_SSL_ENABLED=false` |
| | `server.servlet.session.cookie.secure` | `true` | `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` |
| | `openapi.server-url` | `https://davidharris.net:8080` | `OPENAPI_SERVER_URL=http://localhost:8080` |
| | `server.ssl.certificate`, `…-private-key` | `~/certs/davidharris.net/…pem` | not read once SSL is off |
| `preferences-angular/user-app/angular.json` → `serve.options` | `host` | `davidharris.net` | `--host localhost` |
| | `port` | `8445` | `--port 4200` |
| | `ssl`, `sslCert`, `sslKey` | `true`, `~/certs/…pem` | `--ssl=false` (the cert paths are then ignored) |
| | `proxyConfig` | `proxy.conf.json` → `https://davidharris.net:8080` | `--proxy-config <file outside the repo>` |
| `preferences-angular/user-app/package.json` | `start` script | adds `NODE_EXTRA_CA_CERTS` (the mkcert CA) | not used: run `npx ng serve` |

How the overrides win:
- **Spring Boot** ranks environment variables above `application.properties`. `server.ssl.enabled` becomes `SERVER_SSL_ENABLED`: uppercase, with `.` and `-` replaced by `_`.
- **Angular** ranks command-line flags above `angular.json`. Each flag matches one key: `--proxy-config` is `proxyConfig`.

---

## 2. Why no CORS configuration is needed

The app calls *relative* URLs (`/users`, `/users/1`). The browser sends them to the page's own origin, the dev server, and the dev server's proxy forwards them to Spring:

```
Browser ──http://localhost:4200/users/1──▶ ng serve (4200) ──proxy──▶ Spring (localhost:8080)
          same origin as the page            Node makes this hop; only browsers enforce CORS
```

To the browser, every request is same-origin, so CORS never comes up. That's why the backend has no CORS configuration, and why the localhost run doesn't need one either. It works only because the proxy is in place: if the browser called `localhost:8080` directly, it would be cross-origin and blocked ([section 6](#6-if-you-must-change-a-file-keep-it-out-of-commits)).

---

## 3. One-time setup: a proxy file outside the repo

This file is read by the Angular dev server (`ng serve`), not by Spring Boot. The proxy setting has to be a file; `ng serve` has no flag for the target itself. The committed `proxy.conf.json` points at `https://davidharris.net:8080`, so you need one that points at localhost. Keep it outside the repo, where it can't be committed:

```sh
mkdir -p ~/.config/preferences-dev
cat > ~/.config/preferences-dev/proxy.localhost.json <<'EOF'
{
  "/users": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
EOF
```

- `target`: where the dev server forwards `/users…`.
- `secure`: whether the proxy checks the backend's certificate. Over `http://` there's no certificate, so `false`.

---

## 4. Start both projects

### 4.1 Backend

```sh
cd ~/dev/preferences-work/preferences-spring/preferences
SERVER_SSL_ENABLED=false \
SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
OPENAPI_SERVER_URL=http://localhost:8080 \
./gradlew bootRun
```

The same overrides as command-line arguments, if you prefer them (they rank even higher):

```sh
./gradlew bootRun --args='--server.ssl.enabled=false --server.servlet.session.cookie.secure=false --openapi.server-url=http://localhost:8080'
```

**Check** the log: `Tomcat started on port 8080 (http)`. `(http)` is the proof; production logs `(https)`. If start-up instead fails with an error about a missing certificate or key file, SSL is still on: an override is misspelled.

**Check** from another terminal:

```sh
/usr/bin/curl -i http://localhost:8080/users/1   # 200 with JSON, or 404 if user 1 doesn't exist
```

Swagger UI is at `http://localhost:8080/swagger-ui.html`, and its "Try it out" goes to `http://localhost:8080`.

### 4.2 Frontend

```sh
cd ~/dev/preferences-work/preferences-angular/user-app
npx ng serve --host localhost --port 4200 --ssl=false \
  --proxy-config ~/.config/preferences-dev/proxy.localhost.json
```

| Flag | What happens without it |
|---|---|
| `--host localhost` | `host` stays `davidharris.net`. Without the hosts entry it doesn't resolve, and the dev server won't start (`ENOTFOUND`) |
| `--port 4200` | port 8445, which works but isn't the usual development port |
| `--ssl=false` | the dev server looks for the certificate files and fails |
| `--proxy-config …` | `/users` goes to davidharris.net, and Load and Save fail with HTTP 502 or 504 |

Use `npx ng serve`, not `npm start`: the `start` script adds `NODE_EXTRA_CA_CERTS` for the mkcert CA, which a plain-HTTP chain doesn't need, and which isn't on a machine without the certificates.

**Check** the output: `➜  Local:   http://localhost:4200/`.

### 4.3 Verify in the browser

Open **`http://localhost:4200`**. Use `localhost` exactly: the dev server listens on `localhost` (IPv6 `[::1]`) only, so `127.0.0.1:4200` doesn't connect.

1. Fill in the form and click **Save**. Expect "Saved … (id N)."
2. Type that id into **User ID** and click **Load**. Expect the form to fill in.
3. DevTools → **Network**: requests go to `http://localhost:4200/users…`. There are no `OPTIONS` requests and no CORS messages.

---

## 5. Before you commit

```sh
cd ~/dev/preferences-work
git status
```

If you followed this guide, it shows nothing from the localhost run.

**Watch out for the Angular folder.** In this repo, `preferences-angular/user-app` is recorded as a gitlink (a pointer to a commit of another repository), not as ordinary files. **`git status` here doesn't show changes inside it.** If you edit an Angular file, this repo won't warn you. Check, and commit from, the Angular project's own repository.

---

## 6. If you must change a file: keep it out of commits

Two things can't be done with flags alone. Do them only if your work needs them.

| You need | The file change | Why flags aren't enough |
|---|---|---|
| The Angular tests on localhost | in `src/app/app.spec.ts`, `const BACKEND = 'https://davidharris.net:8080'` → `'http://localhost:8080'` | it's a hard-coded constant; every test first sends a real request to it |
| The browser to call the backend directly (cross-origin, so you can see or test CORS) | an API base URL in `user.service.ts` with per-environment files, and a `CorsConfig` class in Spring | relative URLs always go to the page's origin, and Spring Boot has no built-in property for CORS on your own controllers |

To keep such changes local:

- **A changed tracked file:** check `git diff` before every commit, and undo the change with `git restore <file>` when you're done. Don't stage with `git add .` or `git commit -a` while it's changed.
- **A new local-only file:** list it in `.git/info/exclude`. That file works like `.gitignore`, but it's never committed or shared, so `git status` stops showing the file:
  ```sh
  echo 'preferences-spring/preferences/src/main/java/org/bighouse/demo/config/CorsConfig.java' >> .git/info/exclude
  ```
- **Anything under `preferences-angular/user-app`:** do the same in the Angular project's own repository ([section 5](#5-before-you-commit)).

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend fails at start-up with a missing certificate or key file | SSL is still on: an override is misspelled or missing | 4.1 |
| `Bad Request. This combination of host and port requires TLS.` | `http://` sent to a backend that's still on HTTPS | 4.1 |
| `ng serve` fails with `ENOTFOUND davidharris.net` | `--host localhost` is missing | 4.2 |
| `ng serve` fails looking for a `.pem` file | `--ssl=false` is missing | 4.2 |
| Load or Save says "(HTTP 502)" or "(HTTP 504)"; the `ng serve` log shows `http proxy error` | `--proxy-config` is missing (the proxy goes to davidharris.net), or the backend isn't running | 4.1, 4.2 |
| "could not reach the server. Is the backend running on https://davidharris.net:8080?" | The browser got no response at all: the backend is down, or the tab is an old `https://davidharris.net:8445` page. The URL in the message is fixed text, not the URL that was called | open `http://localhost:4200`; check 4.1 |
| `http://127.0.0.1:4200` doesn't connect | The dev server listens on `localhost` (`[::1]`) only | use `http://localhost:4200` |
| `curl: (60) SSL certificate problem` | Anaconda's `curl` | use `/usr/bin/curl` |
| `Port 4200 is already in use` / `Port 8080 was already in use` | An earlier server is still running | stop it (Ctrl+C in its terminal) |

---

## 8. Back to production

Nothing was changed, so there's nothing to undo. On a machine with the certificates and the hosts entry, start the committed configuration:

```sh
cd ~/dev/preferences-work/preferences-spring/preferences && ./gradlew bootRun   # in a fresh shell, without the overrides
cd ~/dev/preferences-work/preferences-angular/user-app && npm start              # https://davidharris.net:8445
```
