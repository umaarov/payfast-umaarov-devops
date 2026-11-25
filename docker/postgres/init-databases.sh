#!/bin/sh
set -eu

create_database() {
    name="$1"
    password="$2"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        -v name="$name" -v password="$password" <<'SQL'
CREATE ROLE :"name" LOGIN PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
SQL
}

create_database orders "$ORDER_DB_PASSWORD"
create_database warehouse "$WAREHOUSE_DB_PASSWORD"
create_database delivery "$DELIVERY_DB_PASSWORD"
create_database tracking "$TRACKING_DB_PASSWORD"
