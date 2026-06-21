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
 */
package pro.mir0n.esquire.dataKeep.keep;

/** A keep's DB sink datasource + pool (bound from a leg's datasource group). The keep builds its OWN
 *  auto-commit pool from this -- a keep applies relayed events outside any caller transaction. */
public record KeepDataSourceParams(String url, String username, String password, Hikari hikari,
                                   Boolean shared) {

    /** Connection-pool settings, mirroring spring.datasource.hikari -- the keep DB is configured the same way
     *  as a service DB, just in its own group. All nullable -> Hikari/our defaults apply. */
    public record Hikari(Integer maximumPoolSize, Integer minimumIdle, Long connectionTimeout,
                         Long maxLifetime, Long idleTimeout) { }

    /** True iff the keep should REUSE the service's own DataSource (shared pool) instead of building its own
     *  dedicated pool from {@code url}/{@code hikari}. When shared, the dialect comes from the service's
     *  DataSource URL (spring.datasource.url). */
    public boolean isShared() { return Boolean.TRUE.equals(shared); }

    public Hikari hikariOrEmpty() {
        return hikari != null ? hikari : new Hikari(null, null, null, null, null);
    }
}
