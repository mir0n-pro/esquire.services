package pro.mir0n.esquire.enyMan.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.common.EsqMsgConstants;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KcResponseListenerTest {

    private KcResponseListener listener;

    @BeforeEach
    void setUp() {
        listener = new KcResponseListener();
    }

    private Message buildMessage(String msgType) throws JMSException {
        Message msg = mock(Message.class);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE)).thenReturn(msgType);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenReturn("mid-001");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE)).thenReturn(EsqMsgConstants.EVENT_UPDATE_PATH);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID)).thenReturn("uid-1");
        when(msg.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND)).thenReturn(20);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_CTRL_ID)).thenReturn("enyman.test");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID)).thenReturn("rid-1");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID)).thenReturn("cid-1");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID)).thenReturn("rid-1");
        return msg;
    }

    @Test
    @DisplayName("onResponse: URS message processed without exception")
    void onResponse_ursMessage_noException() throws JMSException {
        Message msg = buildMessage(EsqMsgConstants.MSG_TYPE_RESPONSE);
        assertThatNoException().isThrownBy(() -> listener.onResponse(msg));
    }

    @Test
    @DisplayName("onResponse: URR message processed without exception")
    void onResponse_urrMessage_noException() throws JMSException {
        Message msg = buildMessage(EsqMsgConstants.MSG_TYPE_REJECT);
        assertThatNoException().isThrownBy(() -> listener.onResponse(msg));
    }

    @Test
    @DisplayName("onResponse: JMS exception does not propagate")
    void onResponse_jmsException_noException() throws JMSException {
        Message msg = mock(Message.class);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_MSG_TYPE)).thenThrow(new JMSException("read error"));
        assertThatNoException().isThrownBy(() -> listener.onResponse(msg));
    }

    @Test
    @DisplayName("onResponse: null field values handled without exception")
    void onResponse_nullFields_noException() {
        // Mockito returns null for all getStringProperty calls and 0 for getIntProperty by default
        Message msg = mock(Message.class);
        assertThatNoException().isThrownBy(() -> listener.onResponse(msg));
    }
}
