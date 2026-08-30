/*
 *  Esquire frameworks (tm)
 *  tp-sqns -- transport provider (Amazon SQS / Amazon SNS)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the pieces the sqs and sns providers share -- the client build (region, the
 *                   endpoint override LocalStack needs, the SDK default credential chain), the queue-name
 *                   rules (route-by and the character set SQS allows), the create-or-get queue URL cache,
 *                   the transport.params prefix groups (client. / queue. / topic. / subscription.) and the
 *                   gate that REFUSES a param naming no AWS call rather than dropping it.
 */
package pro.mir0n.esquire.tp.sqns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.BusIdentity;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The queue and client rules the SQS-backed providers share. */
public final class SqsSupport {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.sqns.SqsSupport");

    /** The AWS region the client works in. Absent = the SDK default chain (AWS_REGION and the rest). */
    public static final String PARAM_REGION = "region";

    /**
     * The header whose VALUE splits a destination into one queue per value.
     *
     * <p>SQS has no message selector, and the R&amp;R protocol needs one: a CLIENT takes only its own replies
     * off the response node, a SERVER only its slot's requests off the request node. So the filter becomes a
     * destination -- the node declares which header names the queue, and both sides agree on it without either
     * having to read the other's mind. A publisher takes the value off the message it is sending (the server
     * echoes the requester's rod-id onto the reply, so the reply carries the queue it belongs in); a consumer
     * takes the value off its own identity.
     */
    public static final String PARAM_ROUTE_BY = "route-by";

    /** SQS long-poll seconds on a receive; 20 is the maximum SQS accepts. */
    public static final String PARAM_WAIT_SECONDS = "wait-seconds";

    /** Messages asked for per receive; 10 is the maximum SQS accepts. */
    public static final String PARAM_BATCH_SIZE = "batch-size";

    /**
     * The param PREFIXES that name which AWS call a key belongs to.
     *
     * <p>The other drivers hand the whole {@code transport.params} group to the vendor verbatim, because their
     * vendor has ONE place to take it: ActiveMQ parses broker-URI options, Kafka takes one flat config map,
     * Lettuce takes {@code redis://} URI options. AWS has no such single place -- a setting belongs to the SDK
     * client, or to CreateQueue, or to CreateTopic, or to Subscribe -- so the prefix names the call, and the
     * key after it is the vendor's OWN name, handed on with no per-key code here.
     */
    public static final String GROUP_CLIENT = "client";
    public static final String GROUP_QUEUE = "queue";
    public static final String GROUP_TOPIC = "topic";
    public static final String GROUP_SUBSCRIPTION = "subscription";

    /** SDK client settings are NOT a verbatim map -- the builder takes typed values -- so these few are read by
     *  name and anything else under {@code client.} is refused rather than quietly ignored. */
    private static final String CLIENT_API_CALL_TIMEOUT = "apiCallTimeout";
    private static final String CLIENT_API_CALL_ATTEMPT_TIMEOUT = "apiCallAttemptTimeout";
    private static final String CLIENT_MAX_ATTEMPTS = "maxAttempts";

    private SqsSupport() {
    }

    /**
     * The sub-group of params written {@code <prefix>.<key>}, keyed by {@code <key>} alone.
     *
     * <p>It lives HERE, in the driver, and not on {@code TransportSettings} where it would read more naturally.
     * The reason is the deployment shape: AWS is ATTACHED, so these jars are mounted beside a service image
     * that already carries its own build of the messaging framework. A driver may therefore only call
     * framework API the SHIPPED image has -- adding a method to {@code TransportSettings} for the drivers'
     * sake would make every service image need rebuilding, which is exactly what attaching was meant to avoid.
     */
    public static Map<String, String> paramGroup(Map<String, String> params, String prefix) {
        Map<String, String> ret = new LinkedHashMap<>();
        String head = prefix + ".";
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith(head) && e.getKey().length() > head.length()) {
                ret.put(e.getKey().substring(head.length()), e.getValue());
            }
        }
        return ret;
    }

    /** Refuse a param this driver does not know. A bare key that is not one of {@code known}, or a prefixed key
     *  whose prefix is not one of {@code groups}, has nowhere to go -- there is no further AWS call to hand it
     *  to -- and dropping it in silence is how a leg ends up running without a setting the topology says it
     *  has. It is a topology mistake, and it fails at open. */
    public static void requireKnownParams(String tag, Map<String, String> params,
                                          Set<String> known, Set<String> groups) {
        for (String key : params.keySet()) {
            int dot = key.indexOf('.');
            boolean ok;
            if (dot > 0) {
                ok = groups.contains(key.substring(0, dot));
            } else {
                ok = known.contains(key);
            }
            if (!ok) {
                throw new IllegalStateException(tag + ": unknown transport param '" + key
                        + "'; expected one of " + known + " or a key under " + groups);
            }
        }
    }

    /** The SDK client override built from the {@code client.} group. */
    static ClientOverrideConfiguration clientOverride(String tag, Map<String, String> group) {
        ClientOverrideConfiguration.Builder builder = ClientOverrideConfiguration.builder();
        for (Map.Entry<String, String> e : group.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (CLIENT_API_CALL_TIMEOUT.equals(key)) {
                builder.apiCallTimeout(Duration.ofMillis(Long.parseLong(value.trim())));
            } else if (CLIENT_API_CALL_ATTEMPT_TIMEOUT.equals(key)) {
                builder.apiCallAttemptTimeout(Duration.ofMillis(Long.parseLong(value.trim())));
            } else if (CLIENT_MAX_ATTEMPTS.equals(key)) {
                int attempts = Integer.parseInt(value.trim());
                builder.retryStrategy(b -> b.maxAttempts(attempts));
            } else {
                throw new IllegalStateException(tag + ": unknown client param 'client." + key
                        + "'; the SDK client takes typed settings, not a verbatim map");
            }
        }
        return builder.build();
    }

    /** An SQS client for this leg. The endpoint is set only when the topology gives one -- that is the
     *  LocalStack case; against real AWS it is left empty and the SDK builds the endpoint from the region.
     *  Credentials always come from the SDK default chain, so none is ever written into a file. */
    public static SqsClient client(String endpoint, Map<String, String> params, Map<String, String> clientGroup) {
        SqsClientBuilder builder = SqsClient.builder();
        String region = params.get(PARAM_REGION);
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region.trim()));
        }
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        if (clientGroup != null && !clientGroup.isEmpty()) {
            builder.overrideConfiguration(clientOverride("tp-sqs", clientGroup));
        }
        return builder.build();
    }

    /** The queue a message is SENT to: the destination, split by the value of the {@code route-by} header the
     *  message carries. A node that declares {@code route-by} and a message that does not carry it is a defect,
     *  not a case to work around -- the send would land in a queue nobody drains -- so it throws. */
    public static String publishQueueName(String destination, String routeBy, Map<String, Object> headers) {
        String ret = destination;
        if (routeBy != null && !routeBy.isBlank()) {
            Object value = headers.get(routeBy);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalStateException("tp-sqs: the leg routes by " + routeBy
                        + " but the message carries no such field; destination " + destination);
            }
            ret = destination + "-" + value;
        }
        return sanitize(ret);
    }

    /** The queue a leg CONSUMES from: the destination, split by this rod's OWN identity under the same
     *  {@code route-by} rule -- {@code RodID} is its rod-id, {@code SlotID} its slot-id, {@code BusID} its
     *  bus-id. That is the split the JMS selector used to make, moved from a filter onto a destination. */
    public static String consumeQueueName(String destination, String routeBy, BusIdentity identity) {
        String ret = destination;
        if (routeBy != null && !routeBy.isBlank()) {
            String value = identityValue(routeBy, identity);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("tp-sqs: the leg routes by " + routeBy
                        + " but this rod has no such identity field; destination " + destination);
            }
            ret = destination + "-" + value;
        }
        return sanitize(ret);
    }

    /** The queue URL for {@code name}, created when it is not there yet. CreateQueue returns the existing queue
     *  for a name already taken, so this is also "make sure it exists" -- which is what lets a server reply to a
     *  client whose own consumer has not started yet. */
    public static String queueUrl(SqsClient sqs, String name, Map<String, String> cache,
                                  Map<String, String> attributes) {
        String ret = cache.get(name);
        if (ret == null) {
            ret = createQueue(sqs, name, attributes);
            cache.put(name, ret);
        }
        return ret;
    }

    /** Make the queue and take its URL. CreateQueue returns the queue that is already there, so this is both
     *  "create it" and "make sure it is still there" -- the second is what lets a leg come back after the
     *  queue was removed under it. */
    public static String createQueue(SqsClient sqs, String name, Map<String, String> attributes) {
        CreateQueueRequest.Builder request = CreateQueueRequest.builder().queueName(name);
        if (attributes != null && !attributes.isEmpty()) {
            request.attributesWithStrings(attributes);   // the vendor's own attribute names, verbatim
        }
        String ret = sqs.createQueue(request.build()).queueUrl();
        // once per queue, not per message: with route-by the queue is worked out per message, and which queue
        // a leg actually reached is the first thing anyone asks when a reply does not arrive.
        devLog.info("tp-sqs: queue in use: {}", ret);
        return ret;
    }

    private static String identityValue(String routeBy, BusIdentity identity) {
        String ret = null;
        if (identity != null) {
            if (BusConstants.FIELD_ROD_ID.equals(routeBy)) {
                ret = identity.rodId();
            } else if (BusConstants.FIELD_SLOT_ID.equals(routeBy)) {
                ret = identity.slotId();
            } else if (BusConstants.FIELD_BUS_ID.equals(routeBy)) {
                ret = identity.busId();
            }
        }
        return ret;
    }

    /** An SQS queue name takes letters, digits, hyphen and underscore, and stops at 80 characters. Bus
     *  destinations and rod-ids are dotted (esquire.kc.request, enyman.0), so every other character becomes a
     *  hyphen. The length is not checked here: SQS refuses an over-long name with a clear message of its own. */
    public static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (keep) {
                out.append(c);
            } else {
                out.append('-');
            }
        }
        return out.toString();
    }
}
