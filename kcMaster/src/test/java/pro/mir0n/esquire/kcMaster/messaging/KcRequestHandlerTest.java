package pro.mir0n.esquire.kcMaster.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.backend.identity.AuthSyncRequest;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.esquire.kcMaster.service.IKcIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class KcRequestHandlerTest {

    @Mock
    private IKcIdentityService kcIdentityService;

    private KcRequestHandler handler;

    @BeforeEach
    void setUp() {
        handler = new KcRequestHandler(kcIdentityService);
    }

    // --- helpers ---

    private AuthSyncRequest buildCreateReq() {
        AuthSyncRequest req = new AuthSyncRequest();
        req.setId("uid-001");
        req.setKind(998);
        req.setLoginId("alice@example.com");
        req.setEmail("alice@example.com");
        return req;
    }

    private AuthSyncRequest buildUpdateReq(String tfaMethod, String pwdChangeForced) {
        AuthSyncRequest req = new AuthSyncRequest();
        req.setId("uid-001");
        req.setKind(998);
        req.setLoginId("alice@example.com");
        req.setEmail("alice@example.com");
        req.setTfaMethod(tfaMethod);
        req.setPwdChangeForced(pwdChangeForced);
        return req;
    }

    private AuthSyncRequest buildDeleteReq() {
        AuthSyncRequest req = new AuthSyncRequest();
        req.setId("uid-001");
        req.setKind(998);
        req.setLoginId("alice@example.com");
        return req;
    }

    // --- CREATE dispatch ---

    @Test
    @DisplayName("CREATE: delegates to createUser with loginId and email")
    void create_delegatesWithLoginIdAndEmail() {
        handler.handle(BusConstants.EVENT_CREATE, buildCreateReq(), "cid1", "rid1");

        verify(kcIdentityService).createUser(
                eq("alice@example.com"),
                eq("alice@example.com"),
                anyString(),
                eq(true),
                eq(true),
                eq(false),
                eq("cid1"),
                eq("rid1")
        );
    }

    // --- UPDATE dispatch ---

    @Test
    @DisplayName("UPDATE: delegates to updateUserAuthState with loginId")
    void update_delegatesWithLoginId() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq(null, null), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                eq("alice@example.com"), any(), any(), any(), any(),
                any(), eq("cid1"), eq("rid1"));
    }

    @Test
    @DisplayName("UPDATE: tfaMethod 'g' sets requireTotp=true, removeTotp=null")
    void update_tfaMethodG_requiresTotp() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq("g", null), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                anyString(), any(), any(), any(), eq(Boolean.TRUE),
                isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("UPDATE: tfaMethod 'n' sets removeTotp=true, requireTotp=null")
    void update_tfaMethodN_removesTotp() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq("n", null), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                anyString(), any(), any(), any(), isNull(),
                eq(Boolean.TRUE), anyString(), anyString());
    }

    @Test
    @DisplayName("UPDATE: null tfaMethod sets both requireTotp and removeTotp to null")
    void update_nullTfaMethod_neitherTotpFlag() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq(null, null), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                anyString(), any(), any(), any(), isNull(),
                isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("UPDATE: pwdChangeForced 'Y' maps to Boolean.TRUE")
    void update_pwdChangeForcedY_mapsToTrue() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq(null, "Y"), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                anyString(), any(), any(), eq(Boolean.TRUE), any(),
                any(), anyString(), anyString());
    }

    @Test
    @DisplayName("UPDATE: pwdChangeForced not 'Y' maps to false")
    void update_pwdChangeForcedOther_mapsToFalse() {
        handler.handle(BusConstants.EVENT_UPDATE, buildUpdateReq(null, "N"), "cid1", "rid1");

        verify(kcIdentityService).updateUserAuthState(
                anyString(), any(), any(), eq(false), any(),
                any(), anyString(), anyString());
    }

    // --- DELETE dispatch ---

    @Test
    @DisplayName("DELETE: delegates to deleteUser with loginId")
    void delete_delegatesWithLoginId() {
        handler.handle(BusConstants.EVENT_DELETE, buildDeleteReq(), "cid1", "rid1");

        verify(kcIdentityService).deleteUser("alice@example.com", "cid1", "rid1");
        verifyNoMoreInteractions(kcIdentityService);
    }

    // --- unknown command ---

    @Test
    @DisplayName("unknown command throws IllegalArgumentException")
    void unknownCommand_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                handler.handle("Z", buildCreateReq(), "cid1", "rid1")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Z");
    }
}
