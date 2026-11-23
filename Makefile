# Start the backend and frontend in either environment. Run each target in its own terminal.
# See LOCALHOST.md for what each setting does.
#
#   make davidharris.net-back    https://davidharris.net:8080, CORS allows https://davidharris.net:8445
#   make davidharris.net-front   https://davidharris.net:8445, browser calls the backend directly
#   make localhost-back          http://localhost:8080, CORS allows http://localhost:4200
#   make localhost-front         http://localhost:4200, browser calls the backend directly
#
# Switches:
#   PROXY=1   frontends: relative URLs through the dev-server proxy (same-origin, CORS never applies)
#   CORS=0    backends:  no CORS configuration, to see the browser's CORS errors

BACK  := preferences-spring/preferences
FRONT := preferences-angular/user-app
MKCERT_CA := $(HOME)/.local/share/mkcert/rootCA.pem

PROXY ?= 0
CORS  ?= 1

# Clear overrides left in the shell by earlier plain-HTTP runs, so each target gets exactly what it sets.
CLEAN_ENV := env -u SERVER_SSL_ENABLED -u SERVER_SERVLET_SESSION_COOKIE_SECURE -u OPENAPI_SERVER_URL \
	-u SPRING_PROFILES_ACTIVE -u APP_CORS_ALLOWED_ORIGINS

.PHONY: help davidharris.net-back davidharris.net-front localhost-back localhost-front

help:
	@sed -n '1,11p' Makefile | sed 's/^# \{0,1\}//'

# HTTPS with the mkcert certificate. The "direct" profile only adds app.cors.allowed-origins.
davidharris.net-back:
ifeq ($(CORS),0)
	cd $(BACK) && $(CLEAN_ENV) ./gradlew bootRun
else
	cd $(BACK) && $(CLEAN_ENV) SPRING_PROFILES_ACTIVE=direct ./gradlew bootRun
endif

# NODE_EXTRA_CA_CERTS: Node (the dev server and its proxy) doesn't use the system trust store.
davidharris.net-front:
ifeq ($(PROXY),1)
	cd $(FRONT) && NODE_EXTRA_CA_CERTS=$(MKCERT_CA) npx ng serve
else
	cd $(FRONT) && NODE_EXTRA_CA_CERTS=$(MKCERT_CA) npx ng serve -c direct
endif

# Plain HTTP; the certificate isn't read, so it works without ~/certs or the /etc/hosts entry.
# CORS origin passed as a command-line argument (highest precedence; no properties file involved).
LOCALHOST_CORS_ARGS := --args='--app.cors.allowed-origins=http://localhost:4200'

localhost-back:
ifeq ($(CORS),0)
	cd $(BACK) && $(CLEAN_ENV) SERVER_SSL_ENABLED=false SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
		OPENAPI_SERVER_URL=http://localhost:8080 ./gradlew bootRun
else
	cd $(BACK) && $(CLEAN_ENV) SERVER_SSL_ENABLED=false SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
		OPENAPI_SERVER_URL=http://localhost:8080 ./gradlew bootRun $(LOCALHOST_CORS_ARGS)
endif

# No certificate anywhere in this chain, so no NODE_EXTRA_CA_CERTS.
localhost-front:
ifeq ($(PROXY),1)
	cd $(FRONT) && npx ng serve --host localhost --port 4200 --ssl=false --proxy-config proxy.localhost.json
else
	cd $(FRONT) && npx ng serve -c localhost
endif
