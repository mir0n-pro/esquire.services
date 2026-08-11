/*
 *  Esquire frameworks (tm)
 *  KcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/10/2026 mir0n  created (v1.2.12): a moved entity's new path parked with the PATH change number that
 *                   produced it, so the race-8c buffer keeps the newest arrival rather than the last one.
 *                   Orders itself by that number (Comparable), which is all ExpiringCache.storeIfGreater
 *                   needs; an absent number never displaces a numbered path.
 */
package pro.mir0n.esquire.kcMaster.messaging;

/**
 * A moved entity's new path, parked until its KeyCloak user exists, together with the change number that says
 * WHICH move produced it.
 *
 * <p>The number is the {@code esq_entity_path} counter, not the entity row's -- see the declared exception on
 * {@code BusConstants.FIELD_CHANGE_NO}: a path event carries the path's own number. Comparing it against an
 * entity number would be meaningless, which is why nothing here ever sees an entity number.
 *
 * <p><b>It orders itself by that number</b>, which is what lets the park keep the newest arrival without
 * being told how: {@code ExpiringCache.storeIfGreater} just calls {@link #compareTo}.
 *
 * @param path     the new materialized path
 * @param changeNo the path change number that produced it; null when the producer sent none
 */
public record ParkedPath(String path, Long changeNo) implements Comparable<ParkedPath> {

    /**
     * By change number: the newer move is the greater value.
     *
     * <p><b>An ABSENT number is the lowest of all.</b> A producer that sends no number tells us nothing about
     * order, so letting it displace a numbered path would silently reinstate last-arrival-wins on the one
     * path we cannot check. It still parks into an empty slot -- better a path with no order than no path.
     *
     * <p>Two arrivals with the SAME number are one move delivered twice: neither is greater, so the first one
     * parked stays and a redelivery costs nothing.
     */
    @Override
    public int compareTo(ParkedPath other) {
        long mine  = changeNo       == null ? Long.MIN_VALUE : changeNo;
        long yours = other.changeNo == null ? Long.MIN_VALUE : other.changeNo;
        return Long.compare(mine, yours);
    }
}
