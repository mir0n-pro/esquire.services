package pro.mir0n.esquire.paacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.backend.service.PerformanceHolder;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.service.PacManCommands;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.RodEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import pro.mir0n.esquire.backend.service.EsqRequestScope;

class BizRequestHandlerTest {

    private PacManCommands commands;
    private BizRequestHandler handler;
    private final List<RodEvent> answers = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        commands = mock(PacManCommands.class);
        ObjectProvider<MeterRegistry> registry = mock(ObjectProvider.class);
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        handler = new BizRequestHandler(commands, om, new EsqRequestScope(registry, false));
        handler.setResultHandler(answers::add);
    }

    private static RodEvent request(String operation, boolean capture, boolean complete) {
        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_OPERATION, operation);
        body.put(EsqConstants.BIZ_KEY_ROOT_PATH, "1.2.");
        body.put(EsqConstants.BIZ_KEY_ROLES, List.of("teller"));
        body.put(EsqConstants.BIZ_KEY_FIELDS, Map.of("typeId", 1, "amount", 10));
        body.put(EsqConstants.BIZ_KEY_CAPTURE, capture);
        return new RodEvent(null, 52, "a1", null, null, 0L, "c1", "r1", complete ? "u1" : null, "gateway.0",
                BusConstants.MSG_TYPE_REQUEST, body);
    }

    @Test
    @DisplayName("an account command runs pacMan's command with the caller's context and answers URS 200")
    void acctAnswersResponse() {
        EsqRequestContext[] seen = new EsqRequestContext[1];
        when(commands.esquireCommandAcct(eq(52), eq("a1"), eq("acct"), anyMap()))
                .thenAnswer(inv -> {
                    seen[0] = EsqContextHolder.get();
                    return new AcctTransactionSingle();
                });

        handler.serve(request(EsqConstants.BIZ_OP_ACCT, true, true));

        assertEquals(1, answers.size());
        RodEvent a = answers.get(0);
        assertEquals(BusConstants.MSG_TYPE_RESPONSE, a.msgType());
        assertEquals("gateway.0", a.rodId());
        assertEquals("r1", a.requestId());
        assertEquals(200, a.body().get(EsqConstants.BIZ_KEY_STATUS));
        assertTrue(a.body().containsKey(EsqConstants.BIZ_KEY_RESULT));
        assertTrue(a.body().containsKey(EsqConstants.BIZ_KEY_OUTER_MS));
        assertEquals(new EsqRequestContext("c1", "r1", "u1", "1.2.", List.of("teller")), seen[0]);
        assertNull(EsqContextHolder.get());
        assertNull(PerformanceHolder.get());
    }

    @Test
    @DisplayName("a message missing a required claim is answered URR 401 and no command runs")
    void missingClaimRejected() {
        handler.serve(request(EsqConstants.BIZ_OP_ACCT, false, false));

        RodEvent a = answers.get(0);
        assertEquals(BusConstants.MSG_TYPE_REJECT, a.msgType());
        assertEquals(401, a.body().get(EsqConstants.BIZ_KEY_STATUS));
        assertFalse(a.body().containsKey(EsqConstants.BIZ_KEY_OUTER_MS));
        verifyNoInteractions(commands);
    }

    @Test
    @DisplayName("an exception is answered with the status REST gives it: permission denied is URR 403")
    void exceptionMapsToRestStatus() {
        when(commands.esquireCommandAcct(anyInt(), any(), any(), anyMap()))
                .thenThrow(new PermissionDeniedException("acct", "acct"));

        handler.serve(request(EsqConstants.BIZ_OP_ACCT, false, true));

        RodEvent a = answers.get(0);
        assertEquals(BusConstants.MSG_TYPE_REJECT, a.msgType());
        assertEquals(403, a.body().get(EsqConstants.BIZ_KEY_STATUS));
        assertTrue(a.body().containsKey(EsqConstants.BIZ_KEY_PROBLEM));
        assertNull(EsqContextHolder.get());
    }

    @Test
    @DisplayName("an unknown operation is answered URR 400")
    void unknownOperation() {
        handler.serve(request("esq-nope", false, true));

        assertEquals(400, answers.get(0).body().get(EsqConstants.BIZ_KEY_STATUS));
    }
}
