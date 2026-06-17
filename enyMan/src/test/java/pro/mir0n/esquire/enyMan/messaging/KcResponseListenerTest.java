package pro.mir0n.esquire.enyMan.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Unit-tests the bus-oriented KcResponseListener worker: onResponse(RodEvent) logs the receipt; URS vs URR is
 * the event's msg-type. The receive x-Rod wiring (the XRodManager) is mocked.
 */
class KcResponseListenerTest {

    private KcResponseListener listener;

    @BeforeEach
    void setUp() {
        listener = new KcResponseListener(mock(XRodManager.class));
    }

    private RodEvent response(String msgType, Map<String, Object> body) {
        return new RodEvent(RodEvent.Op.UPDATE_PATH, 20, "uid-1", null, 0L,
                "cid-1", "rid-1", null, "enyman.test", msgType, body);
    }

    @Test
    @DisplayName("onResponse: URS processed without exception")
    void onResponse_urs_noException() {
        RodEvent e = response(EsqMsgConstants.MSG_TYPE_RESPONSE, Map.of());
        assertThatNoException().isThrownBy(() -> listener.onResponse(e));
    }

    @Test
    @DisplayName("onResponse: URR (error in body) processed without exception")
    void onResponse_urr_noException() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("title", "KC_SYNC_ERROR");
        error.put("detail", "boom");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        assertThatNoException().isThrownBy(() -> listener.onResponse(response(EsqMsgConstants.MSG_TYPE_REJECT, body)));
    }

    @Test
    @DisplayName("onResponse: null body handled without exception")
    void onResponse_nullBody_noException() {
        RodEvent e = response(EsqMsgConstants.MSG_TYPE_RESPONSE, null);
        assertThatNoException().isThrownBy(() -> listener.onResponse(e));
    }
}
