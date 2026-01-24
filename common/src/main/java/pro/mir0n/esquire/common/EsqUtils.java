/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */
package pro.mir0n.esquire.common;


public class EsqUtils {
	private EsqUtils() {}

    public static String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

}
