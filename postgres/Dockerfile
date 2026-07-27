FROM postgres:17

COPY db.seed/postgres/create     /db-seed/create
COPY db.seed/postgres/create.log /db-seed/create.log
COPY db.seed/postgres/triggers   /db-seed/triggers
COPY db.seed/postgres/fill       /db-seed/fill
RUN chown -R postgres:postgres /db-seed

COPY services/postgres/initdb/init.sh /docker-entrypoint-initdb.d/init.sh
# The CR strip is not paranoia -- this image shipped BROKEN for months. A Windows checkout with
# core.autocrlf=true hands over CRLF, the shebang becomes "#!/bin/bash\r", and the kernel looks for an
# interpreter literally called "bash\r". Postgres then dies at start-up with
#
#     /docker-entrypoint-initdb.d/init.sh: cannot execute: required file not found
#
# which NAMES the script, so it reads as a missing file when the file is right there.
#
# It stayed invisible because initdb scripts run ONLY on an EMPTY data volume: as long as nobody wiped
# the volume, the broken script was never executed. The first from-scratch rebuild found it instantly --
# and a from-scratch rebuild is exactly what a new developer, a new machine, or a fresh deploy does.
# .gitattributes pins *.sh to LF at the source; this makes the IMAGE right even from a checkout that
# predates it. (Same fix as activemq/Dockerfile, same root cause.)
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/init.sh \
 && chmod +x /docker-entrypoint-initdb.d/init.sh
