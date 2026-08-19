/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/16/2026 mir0n  created: v1.2.13 T3.1 -- stamps service=<esquire service> beside application=<process>,
 *                   from the IMeterOwner the running service contributes; no owner means service==application
 */

package pro.mir0n.esquire.backend.o11y;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * The second identity on every meter: {@code application} says which PROCESS produced it, {@code service}
 * says which ESQUIRE SERVICE did. In a classic deployment the two are equal and the boards read as they
 * always did; in a composed one the machine panels stay with the process and the work panels follow the
 * service.
 */
public class EsqServiceTagFilter implements MeterFilter {

    public static final String TAG_SERVICE = "service";

    private final String processName;
    private final IMeterOwner owner;

    public EsqServiceTagFilter(String processName, IMeterOwner owner) {
        this.processName = processName;
        this.owner = owner;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        Meter.Id ret = id;
        if (id.getTag(TAG_SERVICE) == null) {
            String service = null;
            if (owner != null) {
                service = owner.ownerOf(id);
            }
            if (service == null) {
                service = processName;
            }
            ret = id.withTag(Tag.of(TAG_SERVICE, service));
        }
        return ret;
    }
}
