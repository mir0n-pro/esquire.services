/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/26/2026 mir0n  generateEntityId(): epoch-based long id — (ms since esquireEpoch) * 1000 + random sub-ms offset
 */
package pro.mir0n.esquire.common;


public class EsqUtils {
	private EsqUtils() {}

    public static String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

    //xxx: We do not expect the creation of entities to occur very frequently—more often than once per millisecond.
    private static final long esquireEpoch = new java.util.Date("26 Jun 2025 13:20 EDT").getTime();
    //**1,750,958,400,000**
    public static long generateEntityId() {
        return (System.currentTimeMillis() - esquireEpoch) * 1000
             + new java.util.Random().nextInt(1000);
    }

}
