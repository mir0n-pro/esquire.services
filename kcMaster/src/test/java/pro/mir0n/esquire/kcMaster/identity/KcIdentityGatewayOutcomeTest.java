/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/26/2026 mir0n  created: the rig's error listener answers the REJECT -- serve() no longer catches
 */
package pro.mir0n.esquire.kcMaster.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reject moved out of serve() and into onError, so the rig owns the failure path. These pin what the
 * listener answers -- including an Error, which the old catch (Exception) let pass unanswered.
 */
class KcIdentityGatewayOutcomeTest {

    private static final int KIND = 20;

    private KcIdentityGateway gateway() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("keycloak.admin.base-url", "http://localhost:8080/kc-auth");
        env.setProperty("keycloak.admin.realm", "esquire");
        return new KcIdentityGateway(env);
    }

    private RodEvent request() {
        return new RodEvent(RodEvent.Op.CREATE, KIND, "42", null, null, System.currentTimeMillis(),
                "cid-1", "rid-1", null, "kcmaster.0", BusConstants.MSG_TYPE_REQUEST,
                Map.of("loginId", "jdoe"));
    }

    @Test
    @DisplayName("onError answers a REJECT for the failed event, and hands the event back")
    void onError_answersReject() {
        KcIdentityGateway gw = gateway();
        List<RodEvent> answered = new ArrayList<>();
        gw.setResultHandler(answered::add);

        RodEvent event = request();
        RodEvent returned = gw.onError(new IllegalStateException("KC is down"), event);

        assertThat(returned).isSameAs(event);
        assertThat(answered).hasSize(1);
        assertThat(answered.get(0).msgType()).isEqualTo(BusConstants.MSG_TYPE_REJECT);
        assertThat(answered.get(0).correlationId()).isEqualTo("cid-1");
        assertThat(answered.get(0).requestId()).isEqualTo("rid-1");
    }

    @Test
    @DisplayName("an ERROR is answered too -- catch (Exception) used to leave it unanswered")
    void onError_answersAnError() {
        KcIdentityGateway gw = gateway();
        List<RodEvent> answered = new ArrayList<>();
        gw.setResultHandler(answered::add);

        gw.onError(new StackOverflowError("deep"), request());

        assertThat(answered).hasSize(1);
        assertThat(answered.get(0).msgType()).isEqualTo(BusConstants.MSG_TYPE_REJECT);
    }

    @Test
    @DisplayName("the reject body carries the failure detail")
    void rejectBody_carriesTheDetail() {
        KcIdentityGateway gw = gateway();
        List<RodEvent> answered = new ArrayList<>();
        gw.setResultHandler(answered::add);

        gw.onError(new IllegalStateException("KC is down"), request());

        Object error = answered.get(0).body().get("error");
        assertThat(error).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) error).get("detail")).isEqualTo("KC is down");
    }

    @Test
    @DisplayName("no result handler wired: onError does not throw -- a recorder must not take the worker down")
    void onError_withoutResultHandler_isQuiet() {
        KcIdentityGateway gw = gateway();
        RodEvent event = request();

        assertThat(gw.onError(new IllegalStateException("boom"), event)).isSameAs(event);
    }
}
