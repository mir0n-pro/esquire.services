FROM postgres:17

COPY db.seed/postgres/create     /db-seed/create
COPY db.seed/postgres/create.log /db-seed/create.log
COPY db.seed/postgres/triggers   /db-seed/triggers
COPY db.seed/postgres/fill       /db-seed/fill
RUN chown -R postgres:postgres /db-seed

COPY services/postgres/initdb/init.sh /docker-entrypoint-initdb.d/init.sh
RUN chmod +x /docker-entrypoint-initdb.d/init.sh
