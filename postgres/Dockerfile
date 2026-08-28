# The PIN. Every Esquire-built infra image declares two things here, and both are stamped onto the image as
# labels so they travel with the artifact rather than sitting beside it:
# BASE -- the upstream image it is built from. Not a literal in FROM, so a base cannot move on its own: a
#         target that wants a different one passes --build-arg, and says so where the choice is made.
# PIN  -- the release stamp of the release in which what ESQUIRE puts inside this image last changed. It is
#         edited BY HAND, and only then. An image that gained nothing this release keeps the pin it had, so a
#         release cannot replace infrastructure it did not touch.
# The builders read the labels back off the image and tag it with the pin, so the tag is derived rather than
# typed -- and a pin that already names a DIFFERENT image is refused, because that means the content moved and
# nobody said so. The cloud keeps its single release tag for the whole stack; the labels still say what is
# inside it.
# The seed is what Esquire puts in here, so this pin moves when db.seed does -- and DB_VERSION with it.
ARG BASE=postgres:17
ARG PIN=v1.2.13-2608.2320

FROM ${BASE}
ARG BASE
ARG PIN
LABEL pro.mir0n.esquire.base="${BASE}"
LABEL pro.mir0n.esquire.pin="${PIN}"

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
