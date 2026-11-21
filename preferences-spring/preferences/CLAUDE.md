# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a Spring Initializr project (Gradle Kotlin DSL). Everything lives under the `org.bighouse.demo` package, with `DemoApplication` as the `@SpringBootApplication` entry point. Keep new components in that package or its subpackages so component scanning finds them.

Code is grouped by feature. `user/` holds the `User` JPA entity (table `users`: `id`, `name`, `gender`, `age`), a Spring Data `UserRepository`, and `UserController`:

- `GET /users/{id}` returns 200 with the user, or 404
- `POST /users` validates the JSON body with Bean Validation, then returns 201 with a `Location` header

springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`, version pinned in the build because Boot doesn't manage it) generates the API docs from the controller mappings. Swagger UI is at `/swagger-ui.html` and the OpenAPI JSON is at `/v3/api-docs`.

POST takes a `CreateUserRequest` record, which has no `id`. GET and POST both return the `User` entity directly. Swagger descriptions come from `@Operation`, `@ApiResponse` and `@Schema` annotations on the controller and the request record; `config/OpenApiConfig` sets the API title and a single OpenAPI server, `https://davidharris.net:8080` (property `openapi.server-url`, env `OPENAPI_SERVER_URL`), so Swagger UI's "Try it out" sends requests there rather than to the host that served the page.

## Stack

- Java 21 (Gradle toolchain), Spring Boot 4.2.0, Gradle 9.9.2 via the wrapper
- `spring-boot-starter-webmvc`: servlet-based Spring MVC
- `spring-boot-starter-session-jdbc`: HTTP sessions are stored in a relational database through Spring Session JDBC

## Database

The app uses SQLite (`org.xerial:sqlite-jdbc`). The database is the file `preferences.db`, created in the working directory on first run and ignored by `.gitignore`. Set `DB_URL` to use a different file, for example `jdbc:sqlite:/path/to/file.db`.

Setup specific to SQLite in `application.properties`:
- Hibernate has no built-in SQLite dialect, so the build includes `hibernate-community-dialects` and sets `spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect`.
- `spring.session.jdbc.platform=sqlite` selects Spring Session's `schema-sqlite.sql`.
- The Hikari pool is limited to 1 connection because SQLite allows only one writer at a time.

`spring.jpa.hibernate.ddl-auto=update` creates and updates entity tables from the entity classes; there are no migration scripts. `spring.session.jdbc.initialize-schema=always` creates the Spring Session tables on startup.

The app runs on port 8080 by default (`server.port=${SERVER_PORT:8080}`); set `SERVER_PORT` to change it. The Angular frontend (`preferences-angular/user-app`) proxies `/users` to port 8080.

## HTTPS

The app serves HTTPS only, as `https://davidharris.net:8080`. The machine maps `davidharris.net` and `www.davidharris.net` to `127.0.0.1` in `/etc/hosts`.

The certificate is a mkcert certificate outside the project, in `~/certs/davidharris.net/` (`davidharris.net.pem` and `davidharris.net-key.pem`). It covers `davidharris.net`, `www.davidharris.net`, `localhost` and `127.0.0.1`, and it is signed by the local mkcert CA, which is in the system and browser trust stores. That directory's `README.md` and `setup-local-cert.sh` describe how it was made and how to renew or remove it. `application.properties` finds the files through `${HOME}`; override with `SSL_CERT` and `SSL_KEY` (Spring resource URLs, for example `file:/path/to/cert.pem`). The session cookie is marked `Secure`.

Use `/usr/bin/curl` and `/usr/bin/openssl` for checks: the Anaconda versions earlier in `PATH` use their own CA bundle and don't trust the mkcert CA.

## Commands

```bash
./gradlew build                 # compile and package
./gradlew bootRun               # run the app on https://davidharris.net:8080; Swagger UI at /swagger-ui.html
./gradlew bootJar               # executable jar in build/libs/
./gradlew bootBuildImage        # OCI image
```

The project has no tests. No linter or formatter is configured.
