package pro.mir0n.esquire.kcMaster.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KcEntityBroadcastConsumerTest {

    @Mock private Keycloak keycloak;
    @Mock private KeycloakConfig kcConfig;
    @Mock private KcPathBuffer pathBuffer;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private XRodManager rods;

    private KcEntityBroadcastConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new KcEntityBroadcastConsumer(keycloak, kcConfig, pathBuffer, rods);
        when(kcConfig.getRealm()).thenReturn("esquire");
        when(keycloak.realm("esquire")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    @DisplayName("non-X event: ignored; no KC lookup, no buffer write")
    void nonUpdatePathEvent_ignored() {
        consumer.onRodEvent(event(EsqMsgConstants.EVENT_CREATE, "uid-1", 34, Map.of(EsqMsgConstants.TEXT_PATH, "1.x.")));

        verify(usersResource, never()).searchByAttributes(anyString(), any(Boolean.class));
        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    @Test
    @DisplayName("X event, KC user exists: no buffer write (URQ owns the update)")
    void updatePath_userExists_noBuffer() {
        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-001");
        when(usersResource.searchByAttributes(
                eq(EsqConstants.JWT_CLAIM_ENTITY_ID + ":uid-1"), eq(true)))
                .thenReturn(List.of(existing));

        consumer.onRodEvent(event(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, Map.of(EsqMsgConstants.TEXT_PATH, "1.20.")));

        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    @Test
    @DisplayName("X event, KC user missing: path buffered for later flush")
    void updatePath_userMissing_buffers() {
        when(usersResource.searchByAttributes(
                eq(EsqConstants.JWT_CLAIM_ENTITY_ID + ":uid-1"), eq(true)))
                .thenReturn(Collections.emptyList());

        consumer.onRodEvent(event(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, Map.of(EsqMsgConstants.TEXT_PATH, "1.20.")));

        verify(pathBuffer).store("uid-1", "1.20.");
    }

    @Test
    @DisplayName("X event, body has no path: skipped (no buffer write)")
    void updatePath_noPathInBody_skipped() {
        // Even though user missing, no path = nothing to buffer.
        consumer.onRodEvent(event(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, Map.of()));

        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    private RodEvent event(String eventCode, String entityId, int entityKind, Map<String, Object> body) {
        return new RodEvent(RodEvent.opFromCode(eventCode), entityKind, entityId, null, 0L, "cid", "rid", null, body);
    }
}
