/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: the per-service audit-logging settings (read from ${...audit-logging.*}),
 *                   handed to AuditRod.build(). businessProfile = the service's active datasource vendor
 *                   (used for the 'shared' dialect choice).
 */
package pro.mir0n.esquire.common.audit;

public record AuditSettings(
        boolean enabled,
        int poolSize,
        boolean virtualThreads,
        int feedCapacity,
        String logDatastore,
        String logDbVendor,
        String logDbUrl,
        String logDbUsername,
        String logDbPassword,
        int logDbPoolSize,
        String businessProfile) {
}
