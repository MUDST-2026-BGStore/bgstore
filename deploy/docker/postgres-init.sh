#!/bin/sh
set -eu
# The Keycloak database is separate from the application's Flyway-managed database.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE DATABASE keycloak;
SQL
