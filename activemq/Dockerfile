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
ARG BASE=apache/activemq-classic:6.1.4
ARG PIN=v1.2.13-2608.2320

FROM ${BASE}
ARG BASE
ARG PIN
LABEL pro.mir0n.esquire.base="${BASE}"
LABEL pro.mir0n.esquire.pin="${PIN}"

# The broker reads ACTIVEMQ_CONF, which is /opt/apache-activemq/conf. NOT /opt/activemq -- that directory also
# exists in this image but nothing reads it, so a config dropped there is silently ignored and the broker runs
# stock. (That is exactly what happened here until v1.2.11.)
COPY conf/activemq.xml /opt/apache-activemq/conf/activemq.xml

# Prometheus JMX exporter -- a jar loaded into the broker's OWN JVM, reading the local MBean server. It is not a
# process, a sidecar or a bridge: nothing new runs, and no remote JMX port or Jolokia credential is opened. It is
# how the broker -- the one component whose code we do not own -- gets a Prometheus endpoint at all.
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/1.0.1/jmx_prometheus_javaagent-1.0.1.jar /opt/esq-o11y/jmx_prometheus_javaagent.jar
COPY conf/jmx-exporter.yml /opt/esq-o11y/jmx-exporter.yml

# One image serves docker, local k8s and OKE, so everything that differs per target arrives as an environment
# variable and is applied here: the data directory (which volume the store lands on), the usage limits, and the
# observability gate. See the script.
COPY esq-entrypoint.sh /usr/local/bin/esq-entrypoint.sh
# The CR strip is not paranoia. The build context comes from a git checkout, and a Windows checkout with
# core.autocrlf=true hands over CRLF -- the kernel then reads the shebang literally, tries to exec "/bin/sh\r",
# and reports "no such file or directory" NAMING THE SCRIPT, so it reads as a missing file when the file is
# right there. .gitattributes now pins *.sh to LF at the source; this makes the IMAGE correct even from a
# checkout that predates it, or from a clone whose git is configured differently.
RUN sed -i 's/\r$//' /usr/local/bin/esq-entrypoint.sh \
 && chmod 0644 /opt/esq-o11y/jmx_prometheus_javaagent.jar /opt/esq-o11y/jmx-exporter.yml \
 && chmod 0755 /usr/local/bin/esq-entrypoint.sh

EXPOSE 9404

# ENTRYPOINT resets CMD, so the stock command is restated.
ENTRYPOINT ["/usr/local/bin/esq-entrypoint.sh"]
CMD ["activemq", "console"]
