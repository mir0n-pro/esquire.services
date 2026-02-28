/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/01/2026 mir0n added DICT_ACCESS_PROFILE as a virtual entity kind for dictionary
 * 02/12/2026 mir0n  removed CMD_DETAILS
 *                   DICT_ACCESS_PROFILE renamed with KIND_ACCESS_PROFILE
 * 02/28/2026 mir0n  KIND_ADDRESS_POSTAL/BIZ, KIND_PERSON_PRIMARY/SECONDARY/JOINT added
 *                   SUBENTITY_PERSON, SUBENTITY_ADDRESS, SUBENTITY_ADDRESS2 added
 */
package pro.mir0n.esquire.common;


public class EsqConstants {
	private EsqConstants() {}

    public static final String ESQ_CORRELATION_ID = "Esq-Correlation-ID";
    public static final String ESQ_START_TIME = "Esq-Start-Time";
    public static final String ESQ_SERVICE_TIME = "Esq-Service-Time";
    public static final String ESQ_BACKEND_TIME = "Esq-Backend-Time";
    public static final String ESQ_CAPTURE_METRICS = "Esq-Capture-Metrics";

    public static final String X_CORRELATION_ID = "X-Correlation-ID";
    public static final String X_REQUEST_ID = "X-Request-ID";
    public static final String X_RESPONSE_TIME = "X-Response-Time";
    public static final String X_CAPTURE_METRICS = "X-Capture-Metrics"; // The trigger

    public static final String PD_TRACE_ID = "traceId";
    public static final String PD_TIMESTAMP = "timestamp";
    public static final String PD_CORRELATION_ID = "correlationId";
    public static final String PD_REQUEST_ID = "requestId";
    public static final String PD_ERRORS = "errors";
    public static final String PD_STACK_TRACE = "stackTrace";
    public static final String PD_DETAILS = "details";
    public static final String PD_PROCESSING_TIME = "processingTime";


    public static final String  JWT_CLAIM_ENTITY_ID = "esq_uid";
    public static final String  JWT_CLAIM_ENTITY_ROOTPATH = "esq_rootpath";

    public static final int  KIND_ADDRESS_POSTAL = 988;
    public static final int  KIND_ADDRESS_BIZ = 990;
    public static final int  KIND_PERSON_PRIMARY = 992;
    public static final int  KIND_PERSON_SECONDARY = 994;
    public static final int  KIND_PERSON_JOINT = 996;
    public static final int  KIND_ACCESS_PROFILE = 998;

    public static final String  SUBENTITY_PERSON = "person";
    public static final String  SUBENTITY_ADDRESS = "addr";
    public static final String  SUBENTITY_ADDRESS2 = "bizaddr";

}
