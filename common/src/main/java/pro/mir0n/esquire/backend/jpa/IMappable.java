/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: a JPA-level capability -- the object can present itself as a field map
 *                   (property name -> value), filled by its own concrete code (no reflection). Lives at
 *                   the JPA layer so entities implement an interface in their OWN layer; higher features
 *                   (the x-Rod) depend DOWN on it. Lets XYRod.post() take an entity / param row directly.
 */
package pro.mir0n.esquire.backend.jpa;

import java.util.Map;

public interface IMappable {
    /** Put this object's data fields into the map, keyed by property name. */
    void fillMap(Map<String, Object> body);
}
