/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/01/2026 mir0n added DICT_ACCESS_PROFILE as a virtual entity kind for dictionary
 * 02/12/2026 mir0n  removed CMD_DETAILS
 *                   DICT_ACCESS_PROFILE renamed with KIND_ACCESS_PROFILE
 * 02/28/2026 mir0n  KIND_ADDRESS_POSTAL/BIZ, KIND_PERSON_PRIMARY/SECONDARY/JOINT added
 *                   SUBENTITY_PERSON, SUBENTITY_ADDRESS, SUBENTITY_ADDRESS2 added
 * 03/06/2026 mir0n  KIND_ADMIN_ROLE = 980 added
 * 03/09/2026 mir0n  JWT_CLAIM_REALM_ACCESS and JWT_CLAIM_REALM_ACCESS_ROLES added
 * 05/14/2026 mir0n  observability header constants: ESQ_GW_INNER_START_TIME + ESQ_GW_INNER_TIME added;
 *                   ESQ_SERVICE_TIME renamed to ESQ_SRV_OUTER_TIME; ESQ_BACKEND_TIME renamed to
 *                   ESQ_SRV_INNER_TIME; ESQ_CAPTURE_METRICS added for the hauberk trigger
 * 06/04/2026 mir0n  PD_UID ("uid") added: MDC key for the acting user (unified request context)
 * 06/05/2026 mir0n  KIND_ORG_PAR (972) / KIND_USR_PAR (970) added: synthetic x-Rod routing kinds for
 *                   parameter sub-entity audit events (route to the *_par_log writer; param et_pk rides the body)
 * 06/23/2026 mir0n  BUS_KEY_AUDIT/KC/ENTITY, TEXT_* (id/kind/parentId/path/name/desc/status/deleted/ccy),
 *                   FLAG_OPEN, CCY_DEFAULT moved here from common.EsqMsgConstants (the non-wire app constants)
 * 07/08/2026 mir0n  TRACEPARENT ("traceparent") added: the W3C Trace Context header name
 */
package pro.mir0n.esquire.common;


public class EsqConstants {
	private EsqConstants() {}

    public static final String ESQ_CORRELATION_ID = "Esq-Correlation-ID";
    public static final String ESQ_START_TIME = "Esq-Start-Time";
    public static final String ESQ_GW_INNER_START_TIME = "Esq-Gw-Inner-Start-Time";
    public static final String ESQ_GW_INNER_TIME = "Esq-Gw-Inner-Time";
    public static final String ESQ_SRV_OUTER_TIME = "Esq-Srv-Outer-Time";
    public static final String ESQ_SRV_INNER_TIME = "Esq-Srv-Inner-Time";
    public static final String ESQ_CAPTURE_METRICS = "Esq-Capture-Metrics";

    public static final String X_CORRELATION_ID = "X-Correlation-ID";
    public static final String X_REQUEST_ID = "X-Request-ID";
    public static final String TRACEPARENT = "traceparent"; // W3C Trace Context: 00-<traceId>-<spanId>-<flags>
    public static final String X_RESPONSE_TIME = "X-Response-Time";
    public static final String X_CAPTURE_METRICS = "X-Capture-Metrics"; // The trigger

    public static final String PD_TRACE_ID = "traceId";
    public static final String PD_TIMESTAMP = "timestamp";
    public static final String PD_CORRELATION_ID = "correlationId";
    public static final String PD_REQUEST_ID = "requestId";
    public static final String PD_UID = "uid";
    public static final String PD_ERRORS = "errors";
    public static final String PD_STACK_TRACE = "stackTrace";
    public static final String PD_DETAILS = "details";
    public static final String PD_PROCESSING_TIME = "processingTime";


    public static final String  JWT_CLAIM_ENTITY_ID = "esq_uid";
    public static final String  JWT_CLAIM_ENTITY_ROOTPATH = "esq_rootpath";
    public static final String  JWT_CLAIM_REALM_ACCESS = "realm_access";
    public static final String  JWT_CLAIM_REALM_ACCESS_ROLES = "roles";

    public static final int  KIND_ADDRESS_POSTAL = 988;
    public static final int  KIND_ADDRESS_BIZ = 990;
    public static final int  KIND_PERSON_PRIMARY = 992;
    public static final int  KIND_PERSON_SECONDARY = 994;
    public static final int  KIND_PERSON_JOINT = 996;
    public static final int  KIND_ACCESS_PROFILE = 998;
    public static final int  KIND_ADMIN_ROLE = 980;

    // x-Rod synthetic routing kinds for parameter (sub-entity) audit events. Not real entity
    // kinds -- they only route a RodEvent to the matching *_par_log writer in the registry; the
    // parameter's own et_pk travels in the RodEvent body.
    public static final int  KIND_ORG_PAR = 972;
    public static final int  KIND_USR_PAR = 970;

    public static final String  SUBENTITY_PERSON = "person";
    public static final String  SUBENTITY_ADDRESS = "addr";
    public static final String  SUBENTITY_ADDRESS2 = "bizaddr";

    // --- logical bus KEYS a service uses to look up its ref (esquire.<key>.messaging-bus -> {bus-id, slot-id}).
    //     The bus-id / slot-id VALUES are configurable (the topology + refs), NOT hardcoded here. ---
    public static final String BUS_KEY_AUDIT            = "audit-bus";
    public static final String BUS_KEY_KC               = "kc-bus";
    public static final String BUS_KEY_ENTITY           = "entity-bus";

    // --- Text JSON field names (entity state snapshot fields) ---
    public static final String TEXT_ID        = "id";
    public static final String TEXT_KIND      = "kind";
    public static final String TEXT_PARENT_ID = "parentId";
    public static final String TEXT_PATH      = "path";
    public static final String TEXT_NAME      = "name";
    public static final String TEXT_DESC      = "desc";
    public static final String TEXT_STATUS    = "status";
    public static final String TEXT_DELETED   = "deleted";
    public static final String TEXT_CCY       = "ccy";

    // --- Default field values ---
    public static final String FLAG_OPEN      = "O";
    public static final String CCY_DEFAULT    = "USD";

}
