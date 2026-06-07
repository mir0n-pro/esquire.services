/*
 *  Esquire frameworks (tm)
 *  xxRod service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the audit director forwards each event to the XXRod worker pool.
 * 06/06/2026 mir0n  self-configuring director: the pool is now built in init() from config, so the
 *                   forwarding path is exercised end-to-end by RodBusIntegrationTest; here we pin the
 *                   selection contract (type() == TYPE_AUDIT).
 */
package pro.mir0n.esquire.xxRod.director;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditRodDirectorTest {

    @Test
    void declaresAuditType() {
        AuditRodDirector director = new AuditRodDirector(mock(DataSource.class), "xxrod");
        assertThat(director.type()).isEqualTo(IRodDirector.TYPE_AUDIT);
    }
}
