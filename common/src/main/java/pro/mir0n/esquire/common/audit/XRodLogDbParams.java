/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/14/2026 mir0n  created: the bound x-rod.log-db block -- a log-DB SINK an x-Rod applies its events to.
 * 06/15/2026 mir0n  moved messaging -> common.audit + renamed LogDbParams -> XRodLogDbParams: it is XRodLogDb's
 *                   OWN param layer (named after its impl), not a framework type. XRodParams carries an opaque
 *                   x-rod.custom map; this pod binds it into this record via XRodParams.custom(XRodLogDbParams
 *                   .class). The rod owns its own pool built from these params (vendor + jdbc url/user/password +
 *                   pool-size); all fields nullable.
 */
package pro.mir0n.esquire.common.audit;

/** XRodLogDb's own params (bound from the leg's x-rod.custom): the rod builds its pool to apply events here. */
public record XRodLogDbParams(String vendor, String url, String username, String password, Integer poolSize) {

    public String vendorOr(String def)   { return vendor != null && !vendor.isBlank() ? vendor : def; }
    public int    poolSizeOr(int def)     { return poolSize != null ? poolSize : def; }
}
