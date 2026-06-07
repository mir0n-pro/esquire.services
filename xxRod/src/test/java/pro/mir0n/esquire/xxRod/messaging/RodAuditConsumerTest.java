/*
 *  Esquire frameworks (tm)
 *  xxRod service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the audit-queue consumer decodes a JMS message (RodEventCodec.fromMessage)
 *                   and dispatches to the director; a malformed message is swallowed (no propagation).
 */
package pro.mir0n.esquire.xxRod.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.xxRod.director.IRodDirector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RodAuditConsumerTest {

    private static Message message(String text) throws Exception {
        Message m = mock(Message.class);
        when(m.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenReturn("amid-1");
        when(m.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE)).thenReturn(EsqMsgConstants.EVENT_UPDATE);
        when(m.getObjectProperty(EsqMsgConstants.FIELD_ENTITY_KIND)).thenReturn(50);
        when(m.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID)).thenReturn("100");
        when(m.getStringProperty(EsqMsgConstants.FIELD_SUB_ID)).thenReturn(null);
        when(m.getStringProperty(EsqMsgConstants.FIELD_ACTION_TIME)).thenReturn("123");
        when(m.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID)).thenReturn("crl");
        when(m.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID)).thenReturn("req");
        when(m.getStringProperty(EsqMsgConstants.FIELD_UID)).thenReturn("uid");
        when(m.getStringProperty(EsqMsgConstants.FIELD_TEXT)).thenReturn(text);
        return m;
    }

    @Test
    void decodesAndDispatches() throws Exception {
        IRodDirector director = mock(IRodDirector.class);
        new RodAuditConsumer(director, new ObjectMapper())
                .onMessage(message("{\"name\":\"ACC\",\"balance\":10}"));

        ArgumentCaptor<RodEvent> cap = ArgumentCaptor.forClass(RodEvent.class);
        verify(director).accept(cap.capture());
        RodEvent e = cap.getValue();
        assertThat(e.op()).isEqualTo(RodEvent.Op.UPDATE);
        assertThat(e.kind()).isEqualTo(50);
        assertThat(e.entityId()).isEqualTo("100");
        assertThat(e.actionTime()).isEqualTo(123L);
        assertThat(e.uid()).isEqualTo("uid");
        assertThat(e.body()).containsEntry("name", "ACC");
    }

    @Test
    void malformedMessageIsSwallowed() throws Exception {
        IRodDirector director = mock(IRodDirector.class);
        // invalid JSON body -> codec throws -> consumer catches, never dispatches, never propagates
        new RodAuditConsumer(director, new ObjectMapper()).onMessage(message("{ not json"));
        verify(director, never()).accept(any());
    }
}
