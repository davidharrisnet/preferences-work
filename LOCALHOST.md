# Tutorial: run both projects on plain-HTTP localhost, then go back to davidharris.net

This tutorial shows how the two projects are configured for a domain, and how to change that configuration without losing the original. You make every change by hand. Each step says which file to change, shows exactly what to type, and gives a **Check** so you can confirm the result before moving on.

| | Proper configuration (now) | Localhost configuration (this tutorial) |
|---|---|---|
| Backend (`preferences-spring/preferences`) | `https://davidharris.net:8080` | `http://localhost:8080` |
| Frontend (`preferences-angular/user-app`) | `https://davidharris.net:8445` | `http://localhost:4200` |
| Needs `/etc/hosts` entry | yes | no |
| Needs the mkcert certificate and CA | yes | no |

**Where the machine stands now.** You commented out the hosts line, so `davidharris.net` resolves to the live AWS site, not this machine. You also moved `~/certs` to `~/certz`, so the certificate paths in both projects point at nothing. The localhost setup needs neither, so you can leave both broken until [section 6](#6-return-to-the-proper-configuration).

**The approach: add overrides, don't edit values.** Every change below adds a new file, adds a new named configuration, or passes a flag. None of them rewrites an existing davidharris.net value. Going back means *stop using the override*, so you never have to remember what the old value was. The one exception, a test constant, is marked **TEMPORARY EDIT**.

> Use `/usr/bin/curl` in the checks. The `curl` from Anaconda, which comes first in your `PATH`, behaves differently with certificates.

---

## 0. Where "the domain" lives: an inventory

These are every place where `davidharris.net` (or HTTPS for it) is configured. Each part of the tutorial overrides some of them.

### The machine

| Where | What | Status now |
|---|---|---|
| `/etc/hosts` line 14 | `127.0.0.1 davidharris.net www.davidharris.net` | commented out |
| `~/certs/davidharris.net/davidharris.net.pem`, `davidharris.net-key.pem` | the certificate and key | moved to `~/certz/…` |
| `~/.local/share/mkcert/rootCA.pem` | the local CA that signed the certificate, trusted by the system and browsers | unchanged |

### Spring Boot: `preferences-spring/preferences/src/main/resources/application.properties`

| Property | Value | Role |
|---|---|---|
| `server.ssl.enabled` | `true` | HTTPS only; plain HTTP gets `400 Bad Request` |
| `server.ssl.certificate` | `${SSL_CERT:file:${HOME}/certs/davidharris.net/davidharris.net.pem}` | certificate file |
| `server.ssl.certificate-private-key` | `${SSL_KEY:file:${HOME}/certs/davidharris.net/davidharris.net-key.pem}` | key file |
| `server.servlet.session.cookie.secure` | `true` | the session cookie is only sent over HTTPS |
| `openapi.server-url` | `${OPENAPI_SERVER_URL:https://davidharris.net:8080}` | where Swagger UI's "Try it out" sends requests; read by `config/OpenApiConfig.java` |
| `server.port` | `${SERVER_PORT:8080}` | not domain-specific; stays 8080 |

### Angular: `preferences-angular/user-app/`

| File | Setting | Value |
|---|---|---|
| `angular.json` → `projects.user-app.architect.serve.options` | `host` | `davidharris.net` |
| | `port` | `8445` |
| | `ssl` | `true` |
| | `sslCert`, `sslKey` | `/home/fazuskazoo/certs/davidharris.net/…pem` |
| | `proxyConfig` | `proxy.conf.json` |
| `proxy.conf.json` | `/users` → `target` | `https://davidharris.net:8080` (`secure: true`) |
| `package.json` | `start`, `test` scripts | set `NODE_EXTRA_CA_CERTS` to the mkcert CA |
| `src/app/app.ts` | `describe()` error text | "Is the backend running on https://davidharris.net:8080?" (just a message) |
| `src/app/app.spec.ts` | `const BACKEND` | `https://davidharris.net:8080` (a real request before every test) |
| `.vscode/launch.json` | "ng serve" `url` | `https://localhost:8445/` |

---

## 1. How each framework layers configuration

You need two ideas before changing anything.

### 1.1 Spring Boot: many sources, highest wins

Spring Boot reads every property from a stack of sources. A value higher in this list overrides the same property lower down (simplified):

1. **Command-line arguments:** `./gradlew bootRun --args='--server.ssl.enabled=false'`
2. **Environment variables**, through *relaxed binding*: `server.ssl.enabled` becomes `SERVER_SSL_ENABLED`, and `openapi.server-url` becomes `OPENAPI_SERVER_URL`. Uppercase the name, and replace `.` and `-` with `_`.
3. **Profile-specific files:** `application-{profile}.properties`, loaded only when that profile is active (`SPRING_PROFILES_ACTIVE=localhost`)
4. **`application.properties`**

There is also the placeholder `${NAME:default}` inside a value. It isn't a separate layer: it reads `NAME` from the sources above and falls back to `default`. `SSL_CERT` and `OPENAPI_SERVER_URL` work that way in this project.

**Why a profile, here?** Overriding with environment variables works (see [2.1](#21-the-quick-way-environment-variables-for-comparison)), but you have to retype them every time. A profile file collects them in one place that's only active when you ask for it. The proper configuration never sees it.

### 1.2 Angular: options, then configurations, then flags

The `ng serve` settings come from `angular.json`, in three layers:

1. **`options`**: the defaults (`host`, `port`, `ssl`, …)
2. **`configurations.<name>`**: named sets that override `options`. `ng serve` uses `defaultConfiguration` (`development`). `-c name` picks a different one. A comma-separated list such as `development,localhost` applies them left to right.
3. **Command-line flags**: `--host`, `--port`, `--ssl`, `--proxy-config`, … override both. Each flag matches one key: `--proxy-config` is `proxyConfig`.

`serve` doesn't compile the app itself. Its `buildTarget` (`user-app:build:development`) points at a **build** configuration, and build configurations can swap source files at compile time with `fileReplacements`. That's how Angular apps get per-environment values such as an API URL.

### 1.3 The dev-server proxy, and why the app has no CORS today

The browser loads the app from the dev server, for example `http://localhost:4200`. `UserService` calls *relative* URLs (`/users`, `/users/1`), so the browser sends them to that same dev server. The dev server's **proxy** (`proxyConfig`) forwards them to the backend.

To the browser, each request goes to the page's own origin, so it's a **same-origin** request and CORS never comes up. The backend is reached by the dev server (Node), not the browser. That's why the backend has no CORS configuration, and why Stage 1 below works without any.

An **origin** is the scheme, host and port together. `http://localhost:4200` and `http://localhost:8080` are *different* origins. If the browser calls the backend directly, the request is cross-origin, and the backend must allow it through CORS. Stage 2 does exactly that, so you can see the errors.

---

## 2. Stage 1: localhost through the proxy (works, no CORS)

### 2.1 The quick way: environment variables (for comparison)

You don't need to run this. It shows that the profile in 2.2 is just a tidier way of setting the same properties:

```sh
cd ~/dev/preferences_work/preferences-spring/preferences
SERVER_SSL_ENABLED=false \
SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
OPENAPI_SERVER_URL=http://localhost:8080 \
./gradlew bootRun
```

### 2.2 Create the `localhost` Spring profile

**Create** `preferences-spring/preferences/src/main/resources/application-localhost.properties`:

```properties
# Active only with SPRING_PROFILES_ACTIVE=localhost.
# Overrides the davidharris.net/HTTPS settings in application.properties for plain-HTTP localhost.

# Serve plain HTTP. The certificate and key properties are then ignored,
# so the missing ~/certs directory doesn't matter.
server.ssl.enabled=false

# A Secure cookie is never sent over plain HTTP; allow it for localhost.
server.servlet.session.cookie.secure=false

# Swagger UI "Try it out" target.
openapi.server-url=http://localhost:8080
```

**Why each line:**
- `server.ssl.enabled=false`: Tomcat starts a plain HTTP connector. With SSL off, `server.ssl.certificate` and `server.ssl.certificate-private-key` are never loaded.
- `server.servlet.session.cookie.secure=false`: with `true`, the browser would get the Spring Session cookie but never send it back over `http://`.
- `openapi.server-url`: overrides the value from `application.properties`, including its `${OPENAPI_SERVER_URL:…}` default, because profile files rank higher.

### 2.3 Start the backend with the profile

```sh
cd ~/dev/preferences_work/preferences-spring/preferences
SPRING_PROFILES_ACTIVE=localhost ./gradlew bootRun
```

**Check** the startup log for these two lines:

```
The following 1 profile is active: "localhost"
Tomcat started on port 8080 (http) with context path '/'
```

`(http)` is the proof: the proper configuration logs `(https)`. If startup instead fails with an error about the certificate file, the profile isn't active. Check the spelling of `SPRING_PROFILES_ACTIVE` and of the file name.

**Check** from another terminal:

```sh
/usr/bin/curl -i http://localhost:8080/users/1
```

Expected: `HTTP/1.1 200` with JSON, or `HTTP/1.1 404` if user 1 doesn't exist yet. Either way the backend answered over plain HTTP.

**Check** Swagger UI: open `http://localhost:8080/swagger-ui.html`. The **Servers** dropdown should show `http://localhost:8080`, and "Try it out" on `POST /users` should return `201`.

```sh
/usr/bin/curl -s http://localhost:8080/v3/api-docs | grep -o '"servers":[^]]*]'
# "servers":[{"url":"http://localhost:8080"...}]
```

### 2.4 Create a localhost proxy file for Angular

**Create** `preferences-angular/user-app/proxy.localhost.json`:

```json
{
  "/users": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

`target` is where the dev server forwards `/users…`. `secure` controls whether the proxy verifies the backend's certificate; with `http://` there's no certificate, so it's `false`. The original `proxy.conf.json` stays as it is.

### 2.5 Start the frontend with flags

```sh
cd ~/dev/preferences_work/preferences-angular/user-app
npx ng serve --host localhost --port 4200 --ssl=false --proxy-config proxy.localhost.json
```

Each flag overrides one key in `serve.options`:

| Flag | Overrides | Proper value |
|---|---|---|
| `--host localhost` | `host` | `davidharris.net` |
| `--port 4200` | `port` | `8445` |
| `--ssl=false` | `ssl` (so `sslCert`/`sslKey` are ignored) | `true` |
| `--proxy-config proxy.localhost.json` | `proxyConfig` | `proxy.conf.json` |

This uses `npx ng serve` instead of `npm start`: the `start` script adds `NODE_EXTRA_CA_CERTS`, which plain HTTP doesn't need. (`npm start -- --host localhost …` also works; npm passes everything after `--` to `ng serve`.)

**Check** the dev server's output:

```
  ➜  Local:   http://localhost:4200/
```

**Check** in the browser at `http://localhost:4200`:
1. Fill in Name, Age and Gender, then click **Save**. Expect "Saved … (id N)."
2. Type that id into **User ID**, then click **Load**. Expect the form to fill in.
3. Open DevTools → **Network**. The requests go to `http://localhost:4200/users` and `http://localhost:4200/users/N`, the page's own origin. There are no `OPTIONS` requests and no CORS messages in the Console.

Stage 1 is done: the two projects talk over localhost, with the proxy in between.

---

## 3. Stage 2: bypass the proxy and get CORS errors

Now the browser will call the backend directly, at `http://localhost:8080`, from a page on `http://localhost:4200`. That's cross-origin, and the backend has no CORS configuration.

To do that, the app needs an API base URL that can differ per environment. The standard Angular way is environment files swapped at build time.

### 3.1 Create the environment files

**Create** `preferences-angular/user-app/src/environments/environment.ts`:

```ts
/**
 * Default environment, used by `npm start`, `ng build` and `ng test`.
 * apiBaseUrl '' keeps URLs relative (`/users`), so requests go to the page's own origin:
 * through the dev-server proxy in development, the proper davidharris.net setup.
 */
export const environment = {
  apiBaseUrl: '',
};
```

**Create** `preferences-angular/user-app/src/environments/environment.localhost.ts`:

```ts
/**
 * Swapped in for environment.ts by the `localhost` build configuration (angular.json).
 * The browser calls the backend directly: cross-origin, so the backend must allow CORS.
 */
export const environment = {
  apiBaseUrl: 'http://localhost:8080',
};
```

Because `environment.ts` has `apiBaseUrl: ''`, the proper configuration behaves exactly as before.

### 3.2 Use the base URL in `UserService`

**Edit** `preferences-angular/user-app/src/app/user.service.ts`:

```diff
 import { HttpClient } from '@angular/common/http';
 import { Injectable, inject } from '@angular/core';
 import { Observable } from 'rxjs';
+
+import { environment } from '../environments/environment';
```

```diff
 export class UserService {
   private readonly http = inject(HttpClient);
+  /** '' (relative, via the proxy) by default; 'http://localhost:8080' in the localhost build. */
+  private readonly baseUrl = environment.apiBaseUrl;
```

```diff
   create(user: User): Observable<User> {
-    return this.http.post<User>('/users', user);
+    return this.http.post<User>(`${this.baseUrl}/users`, user);
   }
```

```diff
   get(id: number): Observable<User> {
-    return this.http.get<User>(`/users/${id}`);
+    return this.http.get<User>(`${this.baseUrl}/users/${id}`);
   }
```

With `baseUrl` `''`, these produce `/users` and `/users/1`, the same URLs as before. That's why the existing tests (`httpMock.expectOne('/users/7')`) still match.

### 3.3 Add `localhost` configurations to `angular.json`

**Edit** `preferences-angular/user-app/angular.json`. Add the two blocks marked `+`. Everything else stays the same.

Under `projects.user-app.architect.build.configurations`, after `development`:

```diff
             "development": {
               "optimization": false,
               "extractLicenses": false,
               "sourceMap": true
-            }
+            },
+            "localhost": {
+              "fileReplacements": [
+                {
+                  "replace": "src/environments/environment.ts",
+                  "with": "src/environments/environment.localhost.ts"
+                }
+              ]
+            }
           },
           "defaultConfiguration": "production"
```

Under `projects.user-app.architect.serve.configurations`, after `development`:

```diff
             "development": {
               "buildTarget": "user-app:build:development"
-            }
+            },
+            "localhost": {
+              "buildTarget": "user-app:build:development,localhost",
+              "host": "localhost",
+              "port": 4200,
+              "ssl": false,
+              "proxyConfig": "proxy.localhost.json"
+            }
           },
           "defaultConfiguration": "development"
```

**What these do:**
- The **build** configuration `localhost` only swaps the environment file. It sets nothing else, so it's combined with `development` to get the debug-friendly settings: `user-app:build:development,localhost` applies `development` first, then `localhost`.
- The **serve** configuration `localhost` holds the same overrides as the flags in 2.5, saved under a name. `proxyConfig` points to the localhost proxy so nothing tries to reach davidharris.net. In this stage the app uses absolute URLs, so the proxy isn't used.
- `options` and `defaultConfiguration` are untouched, so `npm start` still means davidharris.net.

### 3.4 Start the frontend with the `localhost` configuration

Stop the dev server from Stage 1 (Ctrl+C), then:

```sh
cd ~/dev/preferences_work/preferences-angular/user-app
npx ng serve -c localhost
```

Keep the backend running with the `localhost` profile from 2.3.

### 3.5 See the CORS errors

Open `http://localhost:4200` and DevTools (**Console** and **Network** tabs). The messages below are the typical wording; it varies a little between browser versions.

#### Error 1: Load (GET `/users/1`): the request runs, but the response is blocked

Type an id and click **Load**.

- **Console (Chrome):**
  ```
  Access to fetch at 'http://localhost:8080/users/1' from origin 'http://localhost:4200'
  has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present
  on the requested resource.
  ```
- **Console (Firefox):**
  ```
  Cross-Origin Request Blocked: The Same Origin Policy disallows reading the remote resource
  at http://localhost:8080/users/1. (Reason: CORS header 'Access-Control-Allow-Origin' missing).
  Status code: 200.
  ```
- **Network:** a single `GET` to `localhost:8080`, marked as a CORS error.
- **The app:** "Could not load the user: could not reach the server. Is the backend running on https://davidharris.net:8080?" Angular gets `status 0` whenever the browser blocks a response. The app's message assumes that means the server is down, and the URL in it is hard-coded text (see the inventory), so it's misleading here.

**What happened:** a GET with no custom headers is a *simple request*. The browser sends it straight away, Spring runs it and returns `200` with the user, and **then** the browser sees there's no `Access-Control-Allow-Origin` header and refuses to give the response to JavaScript. Firefox even shows `Status code: 200`. The server did the work, but the page can't read the result.

#### Error 2: Save (POST `/users`): blocked before it's sent

Fill in the form and click **Save**.

- **Console (Chrome):**
  ```
  Access to fetch at 'http://localhost:8080/users' from origin 'http://localhost:4200'
  has been blocked by CORS policy: Response to preflight request doesn't pass access
  control check: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  ```
- **Console (Firefox):** `Cross-Origin Request Blocked: … (Reason: CORS header 'Access-Control-Allow-Origin' missing)` for the preflight, followed by `(Reason: CORS request did not succeed)`.
- **Network:** an **`OPTIONS`** request to `/users` (the *preflight*), and no `POST`.
- **The app:** "Save failed: could not reach the server. …"

**What happened:** a POST with `Content-Type: application/json` is not a simple request. The browser first asks permission with a preflight:

```
OPTIONS /users
Origin: http://localhost:4200
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

Spring has no CORS configuration, so its answer grants nothing. With no `Access-Control-Allow-*` headers in the reply, the browser never sends the POST. **No user is created.** Compare this with Error 1, where the GET did run on the server.

#### Prove that CORS is enforced by the browser, not the server

curl doesn't enforce CORS, so it sees exactly what the server sends:

```sh
# The GET, as the browser sends it (with an Origin header)
/usr/bin/curl -i -H 'Origin: http://localhost:4200' http://localhost:8080/users/1
```
Expected: `HTTP/1.1 200` (or `404`) with JSON, and **no** `Access-Control-Allow-Origin` header.

```sh
# The preflight the browser sends before the POST
/usr/bin/curl -i -X OPTIONS http://localhost:8080/users \
  -H 'Origin: http://localhost:4200' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```
Expected: a response with **no** `Access-Control-Allow-Origin`, `-Methods` or `-Headers` headers. You may see `Vary: Origin` headers; those don't grant anything.

The server answers both. The browser makes the decision, based on headers the server *didn't* send.

---

## 4. Stage 3: fix CORS in Spring (property-driven, off by default)

The fix belongs on the backend: it must state which origins may call it. The configuration below turns on only when a property is set, and only the `localhost` profile sets it. The proper configuration stays exactly as it is now.

### 4.1 Add a CORS configuration class

**Create** `preferences-spring/preferences/src/main/java/org/bighouse/demo/config/CorsConfig.java`:

```java
package org.bighouse.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets browsers on other origins call the /users API directly.
 *
 * Off unless app.cors.allowed-origins is set (for example in application-localhost.properties),
 * so the proper davidharris.net configuration, where the frontend uses a same-origin proxy,
 * is unaffected.
 */
@Configuration
@ConditionalOnProperty("app.cors.allowed-origins")
public class CorsConfig implements WebMvcConfigurer {

	/** Comma-separated exact origins (scheme://host:port), e.g. http://localhost:4200. */
	private final String[] allowedOrigins;

	public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/users/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST")
				.allowedHeaders("Content-Type")
				.exposedHeaders("Location");
	}

}
```

It sits in `config/`, next to `OpenApiConfig`, so component scanning from `DemoApplication` finds it.

**Line by line:**
- `@ConditionalOnProperty("app.cors.allowed-origins")`: Spring creates this bean only when the property exists, so without it the class does nothing.
- `addMapping("/users/**")`: CORS applies to `/users` and everything under it. Swagger and the other paths are unaffected.
- `allowedOrigins(...)`: the browser compares the request's `Origin` header with this list, **exactly**. `http://localhost:4200` doesn't match `http://127.0.0.1:4200` or `https://localhost:4200`.
- `allowedMethods("GET", "POST")`: the methods the controller has. The preflight asked for `POST`.
- `allowedHeaders("Content-Type")`: the preflight asked for `content-type`. Header names are case-insensitive.
- `exposedHeaders("Location")`: by default, JavaScript can't read non-standard response headers on a cross-origin response. `POST` returns `Location`, so this exposes it, even though the app doesn't read it today.

**Why not `"*"`?** `allowedOrigins("*")` lets *any* website's JavaScript call the API from a visitor's browser. Listing the real frontend origin is the safe habit.

**What about cookies?** The app doesn't send credentials. If it ever relies on the Spring Session cookie cross-origin, the backend also needs `.allowCredentials(true)`, which forbids `*`, and Angular must send `withCredentials: true`. On top of that, the cookie's `SameSite` and `Secure` rules apply. That's out of scope here.

### 4.2 Turn it on in the `localhost` profile

**Edit** `preferences-spring/preferences/src/main/resources/application-localhost.properties`. Add:

```properties
# CORS for the frontend calling the backend directly (Angular `ng serve -c localhost`).
# Read by config/CorsConfig.java; without this property CORS stays off.
app.cors.allowed-origins=http://localhost:4200
```

### 4.3 Restart the backend and check

Stop the backend (Ctrl+C) and start it again. The class is new, so it has to be compiled:

```sh
cd ~/dev/preferences_work/preferences-spring/preferences
SPRING_PROFILES_ACTIVE=localhost ./gradlew bootRun
```

**Check** with curl, starting with the preflight:

```sh
/usr/bin/curl -i -X OPTIONS http://localhost:8080/users \
  -H 'Origin: http://localhost:4200' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```
Expected: `HTTP/1.1 200` with:
```
Access-Control-Allow-Origin: http://localhost:4200
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: content-type
Access-Control-Max-Age: 1800
```

**Check** that an origin not on the list is refused:

```sh
/usr/bin/curl -i -X OPTIONS http://localhost:8080/users \
  -H 'Origin: http://evil.example' \
  -H 'Access-Control-Request-Method: POST'
```
Expected: `HTTP/1.1 403` with the body `Invalid CORS request`.

**Check** in the browser at `http://localhost:4200` (the `ng serve -c localhost` server is still running; no restart is needed):
1. **Save**: the Network tab shows `OPTIONS /users` → `200`, then `POST /users` → `201`. The app shows "Saved … (id N)."
2. **Load**: `GET /users/N` → `200`, with `Access-Control-Allow-Origin: http://localhost:4200` in the response headers. The form fills in.
3. The Console has no CORS errors.

Now the browser calls the backend directly, and the backend allows it.

---

## 5. Tests on localhost (optional)

`app.spec.ts` sends a real request to the backend before each test, at a hard-coded URL. With davidharris.net pointing at the live site, every test fails.

**TEMPORARY EDIT:** in `preferences-angular/user-app/src/app/app.spec.ts`:

```diff
-const BACKEND = 'https://davidharris.net:8080';
+const BACKEND = 'http://localhost:8080'; // TEMPORARY for localhost; revert (see LOCALHOST.md section 6)
```

With the backend running on the `localhost` profile:

```sh
cd ~/dev/preferences_work/preferences-angular/user-app
npx ng test --watch=false
```

**Check:** all tests pass. Tests use the default build, so `environment.ts` (`apiBaseUrl: ''`) applies and the mocked URLs are still `/users…`. The tests run in Node, which doesn't enforce CORS, so they'd pass even without Stage 3.

This is the one change that edits an existing value, because a hard-coded constant has nothing to override. Section 6 reverts it.

---

## 6. Return to the proper configuration

Work through these in order. Each step undoes one thing from above, or from what you broke on purpose.

### 6.1 Stop both servers

Press Ctrl+C in the `bootRun` terminal and in the `ng serve` terminal.

### 6.2 Restore the machine

1. **Hosts entry:** uncomment line 14 of `/etc/hosts`:
   ```sh
   sudo sed -i 's/^#127\.0\.0\.1 davidharris\.net/127.0.0.1 davidharris.net/' /etc/hosts
   grep -n davidharris /etc/hosts      # line 14 should no longer start with #
   getent hosts davidharris.net         # 127.0.0.1
   ```
2. **Certificate directory:** move it back:
   ```sh
   mv ~/certz ~/certs
   ```
3. **Verify** with the setup script. Its paths are hard-coded to `~/certs`, so it only works after the move:
   ```sh
   ~/certs/davidharris.net/setup-local-cert.sh verify
   ```
   Expected: `All checks passed.`

### 6.3 Revert the temporary edit

In `src/app/app.spec.ts`, put the constant back:

```ts
const BACKEND = 'https://davidharris.net:8080';
```

### 6.4 Decide what to do with the overrides

None of these have any effect on the proper configuration, so you can keep them for next time or delete them for a clean tree:

| Added | Has no effect unless | To remove |
|---|---|---|
| `src/main/resources/application-localhost.properties` | `SPRING_PROFILES_ACTIVE=localhost` | delete the file |
| `config/CorsConfig.java` | `app.cors.allowed-origins` is set (only the profile above sets it) | delete the file |
| `proxy.localhost.json` | `--proxy-config proxy.localhost.json` or `-c localhost` | delete the file |
| `localhost` build and serve configurations in `angular.json` | `ng serve -c localhost` | remove the two `+` blocks from 3.3 (and the comma added before each) |
| `src/environments/environment.localhost.ts` | the `localhost` build configuration | delete the file |
| `src/environments/environment.ts` + the `baseUrl` change in `user.service.ts` | always active, but `apiBaseUrl: ''` gives the original relative URLs | revert the diff in 3.2, then delete the file |

If you remove `environment.ts`, revert `user.service.ts` first, or the build fails on the missing import. If you remove the `localhost` configurations, delete `environment.localhost.ts` and `proxy.localhost.json` too, since nothing else uses them.

### 6.5 Start the proper configuration

```sh
# Backend: no profile, so application.properties alone applies
cd ~/dev/preferences_work/preferences-spring/preferences
./gradlew bootRun
```
**Check** the log: there should be no `profile is active` line (or `No active profile set, falling back to 1 default profile: "default"`), and `Tomcat started on port 8080 (https)`.

```sh
# Frontend: the default configuration (davidharris.net, port 8445, HTTPS, proxy.conf.json)
cd ~/dev/preferences_work/preferences-angular/user-app
npm start
```

### 6.6 Verify

```sh
/usr/bin/curl -i https://davidharris.net:8080/users/1     # 200 or 404, no certificate error
/usr/bin/curl -i http://localhost:8080/users/1            # 400: "This combination of host and port requires TLS."
```

- Open `https://davidharris.net:8445`: you should see a padlock with no warning, and Save and Load work. The Network tab shows `https://davidharris.net:8445/users…`, through the proxy again.
- Optional: `npm test -- --watch=false` should pass, with the backend running.

You're back to where you started.

---

## 7. Troubleshooting quick reference

| Symptom | Likely cause | See |
|---|---|---|
| Backend fails at startup with an error about a missing certificate or key file | The `localhost` profile isn't active, so SSL is still on and `~/certs` is gone | 2.3 |
| `Bad Request. This combination of host and port requires TLS.` | Plain `http://` sent to a backend that's still on HTTPS | 2.3 |
| `curl: (6) Could not resolve host: davidharris.net` | No `/etc/hosts` entry, and no DNS record for that name answers locally | 6.2 |
| `https://davidharris.net:8080` hangs or times out | The hosts line is commented out, so the name goes to the live AWS load balancer, which has nothing on 8080 | 6.2 |
| `curl: (60) SSL certificate problem` | Using Anaconda's `curl` (its own CA list), or the mkcert CA isn't trusted | use `/usr/bin/curl`; `setup-local-cert.sh verify` |
| Stage 1: app shows "Save failed (HTTP 502)" or "504"; the `ng serve` log shows a proxy error | The dev server is using `proxy.conf.json` (davidharris.net), or the backend isn't running | 2.4, 2.5 |
| Stage 2/3: requests still go to `localhost:4200/users` | The app was started without `-c localhost`, so `environment.ts` (`''`) is in use | 3.4 |
| Stage 3: still `No 'Access-Control-Allow-Origin' header` | The backend wasn't restarted after adding `CorsConfig`, the profile isn't active, or the origin doesn't match exactly (`127.0.0.1` vs `localhost`, wrong port) | 4.2, 4.3 |
| Preflight returns `403 Invalid CORS request` | The page's origin isn't in `app.cors.allowed-origins` | 4.2 |
| `Port 4200 is already in use` / `Port 8080 was already in use` | An earlier server is still running | stop it, or pick another port (and update `allowed-origins`) |
| The browser goes to the live site even though `/etc/hosts` is right | The browser's "secure DNS" (DNS-over-HTTPS) skips `/etc/hosts` | turn it off in browser settings |
