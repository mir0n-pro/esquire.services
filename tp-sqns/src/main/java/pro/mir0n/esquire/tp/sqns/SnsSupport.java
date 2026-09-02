/*
 *  Esquire frameworks (tm)
 *  tp-sqns -- transport provider (Amazon SQS / Amazon SNS)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the SNS pieces -- CreateTopic (create-or-get, topic. attributes), and putting a
 *                   queue onto a topic: the queue policy that lets the topic write to it, raw message
 *                   delivery, and the filter policy CLEARED, since Subscribe applies attributes only when it
 *                   creates a subscription and one left behind goes on dropping messages.
 */
package pro.mir0n.esquire.tp.sqns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** The SNS side: the topic, and wiring one consumer's queue onto it. */
public final class SnsSupport {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.sqns.SnsSupport");

    /** Subscription attribute: deliver the published message as-is, so the SQS body IS the header bag. Without
     *  it SNS wraps the payload in an envelope and the consumer would have to unwrap before decoding. */
    private static final String RAW_MESSAGE_DELIVERY = "RawMessageDelivery";

    /** Subscription attribute: which messages AWS delivers at all. This transport does not use it -- what a leg
     *  wants is decided on the receive side, where it can be seen -- so it is explicitly CLEARED. An empty value
     *  is how SNS removes a filter policy. */
    private static final String FILTER_POLICY = "FilterPolicy";

    private SnsSupport() {
    }

    /** An SNS client for this leg, built the same way the SQS one is: the endpoint only when the topology
     *  gives one (LocalStack), the region from the params or the SDK default chain, credentials always from
     *  the default chain. */
    public static SnsClient client(String endpoint, Map<String, String> params,
                                   Map<String, String> clientGroup) {
        SnsClientBuilder builder = SnsClient.builder();
        String region = params.get(SqsSupport.PARAM_REGION);
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region.trim()));
        }
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        if (clientGroup != null && !clientGroup.isEmpty()) {
            builder.overrideConfiguration(SqsSupport.clientOverride("tp-sns", clientGroup));
        }
        return builder.build();
    }

    /** The topic, made when it is not there yet. CreateTopic returns the topic that already exists, so this is
     *  also how a publisher and a consumer that start in either order end up on the same one. */
    public static String topicArn(SnsClient sns, String destination, Map<String, String> attributes) {
        String name = SqsSupport.sanitize(destination);
        CreateTopicRequest.Builder request = CreateTopicRequest.builder().name(name);
        if (attributes != null && !attributes.isEmpty()) {
            request.attributes(attributes);   // the vendor's own attribute names, verbatim
        }
        String ret = sns.createTopic(request.build()).topicArn();
        devLog.info("tp-sns: topic in use: {}", ret);
        return ret;
    }

    /**
     * Put this consumer's queue on the topic, and make sure it stays wired the way this leg needs.
     *
     * <p>Two things, and both matter: the queue POLICY, because SNS is a different service and a queue refuses
     * writes from it until told otherwise -- LocalStack lets it through, real AWS does not, so leaving this out
     * would work locally and silently deliver nothing in the cloud; and RAW message delivery, so the body stays
     * the header bag.
     *
     * <p>What is NOT here is any own-exclusion. An SNS subscription names an ADDRESS to deliver to, not a
     * consumer with a view of its own, so there is nothing to hang that on -- {@code noLocal} is a filter this
     * transport applies itself, on the rod-id, where it can be seen and cannot fail quietly.
     *
     * <p>Called again whenever the queue had to be made again, so a queue that was removed comes back wired,
     * not merely present.
     */
    public static void subscribeQueue(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl,
                                      Map<String, String> attributes) {
        String queueArn = queueArn(sqs, queueUrl);
        allowTopicToWrite(sqs, queueUrl, queueArn, topicArn);

        SubscribeRequest subscribe = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .returnSubscriptionArn(true)
                .build();
        String subscriptionArn = sns.subscribe(subscribe).subscriptionArn();

        // Subscribe applies attributes only when it CREATES the subscription; on an existing one it returns the
        // same arn and changes nothing. So both are set explicitly here, and a subscription that already existed
        // with something else is corrected rather than quietly kept.
        //
        // The filter policy is CLEARED, not merely left unset. A policy an earlier deployment wrote would stay
        // on the subscription for good and go on dropping messages this leg never asked it to drop -- wiring
        // that depends on what ran here before is wiring nobody can reason about.
        setAttribute(sns, subscriptionArn, RAW_MESSAGE_DELIVERY, "true");
        setAttribute(sns, subscriptionArn, FILTER_POLICY, "");

        // whatever else the leg declared under subscription.*, by the vendor's own attribute names. It runs
        // AFTER the two above, so a topology that means to set one of them can.
        if (attributes != null) {
            for (Map.Entry<String, String> e : attributes.entrySet()) {
                setAttribute(sns, subscriptionArn, e.getKey(), e.getValue());
            }
        }

        devLog.info("tp-sns: queue {} subscribed to {} (raw=true, no filter policy)", queueArn, topicArn);
    }

    /** The policy that lets the topic write into the queue: SNS is another service, and a queue takes nothing
     *  from it until this says so. Scoped to the one topic, not to SNS at large. */
    static String queuePolicy(String queueArn, String topicArn) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Effect\":\"Allow\","
                + "\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\","
                + "\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}"
                + "}]}";
    }

    private static String queueArn(SqsClient sqs, String queueUrl) {
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build();
        return sqs.getQueueAttributes(request).attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private static void allowTopicToWrite(SqsClient sqs, String queueUrl, String queueArn, String topicArn) {
        Map<QueueAttributeName, String> attributes = new LinkedHashMap<>();
        attributes.put(QueueAttributeName.POLICY, queuePolicy(queueArn, topicArn));
        SetQueueAttributesRequest request = SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(attributes)
                .build();
        sqs.setQueueAttributes(request);
    }

    private static void setAttribute(SnsClient sns, String subscriptionArn, String name, String value) {
        SetSubscriptionAttributesRequest request = SetSubscriptionAttributesRequest.builder()
                .subscriptionArn(subscriptionArn)
                .attributeName(name)
                .attributeValue(value)
                .build();
        sns.setSubscriptionAttributes(request);
    }
}
