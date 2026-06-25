/*
 *  Esquire frameworks (tm)
 *  esquire-dataKeep
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/18/2026 mir0n  created: the keep datasource config group -- the database a keep applies its relayed events
 *                   to, PLUS its connection pool, defined in its OWN group (not spring.datasource) so it is read
 *                   the same way whether the keep runs in-process or behind a bus consumer. Generic: a keep
 *                   building a DB sink binds this from its leg's datasource sub-block. All fields nullable. The
 *                   {@code shared} flag (isShared()) selects reuse of the service's OWN pool over a dedicated one.
 * 06/21/2026 mir0n  dropped the vendor field (and vendorOr()); the keep dialect now comes from the database URL --
 *                   the shared keep reads it from the service's spring.datasource.url.
 * 06/22/2026 mir0n  dropped the shared field and isShared(); the keep is always a dedicated pool now -- the record
 *                   carries only url/username/password/hikari.
 * 06/23/2026 mir0n  Hikari record gained dataSourceProperties (bound from data-source-properties, like
 *                   spring.datasource.hikari.data-source-properties) -- driver connection props forwarded verbatim.
 */
package pro.mir0n.esquire.dataKeep.keep;

import java.util.Map;

/** A keep's DB sink datasource + pool (bound from a leg's datasource group). The keep builds its OWN
 *  auto-commit pool from this -- a keep applies relayed events outside any caller transaction. */
public record KeepDataSourceParams(String url, String username, String password, Hikari hikari) {

    /** Connection-pool settings, mirroring spring.datasource.hikari -- the keep DB is configured the same way
     *  as a service DB, just in its own group. All nullable -> Hikari/our defaults apply. {@code dataSourceProperties}
     *  (bound from {@code data-source-properties}, exactly like {@code spring.datasource.hikari.data-source-properties})
     *  are forwarded VERBATIM to the JDBC driver -- e.g. pgjdbc {@code socketTimeout} / {@code tcpKeepAlive} so a
     *  vanished DB fails a health probe fast instead of hanging on a half-open socket. */
    public record Hikari(Integer maximumPoolSize, Integer minimumIdle, Long connectionTimeout,
                         Long maxLifetime, Long idleTimeout, Map<String, String> dataSourceProperties) { }

    public Hikari hikariOrEmpty() {
        return hikari != null ? hikari : new Hikari(null, null, null, null, null, null);
    }
}
