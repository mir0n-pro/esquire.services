#!/bin/bash
set -e

# Redirect \o output files into the mounted log directory
ln -sf /var/log/postgresql/esq2025-create.log /db-seed/create/esq2025-create.log
ln -sf /var/log/postgresql/esq2025-fill.log   /db-seed/fill/esq2025-fill.log

cd /db-seed/create && psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f all.sql
cd /db-seed/fill   && psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f all.sql
