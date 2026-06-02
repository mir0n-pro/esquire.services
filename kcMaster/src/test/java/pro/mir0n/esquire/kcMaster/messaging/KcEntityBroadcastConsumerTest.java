package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.jms.Message;
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
import pro.mir0n.esquire.kcMaster.buffer.KcPathBuffer;
import pro.mir0n.esquire.kcMaster.config.KeycloakConfig;

import java.util.Collections;
import java.util.List;

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

    private KcEntityBroadcastConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new KcEntityBroadcastConsumer(keycloak, kcConfig, pathBuffer);
        when(kcConfig.getRealm()).thenReturn("esquire");
        when(keycloak.realm("esquire")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    @DisplayName("non-X event: ignored; no KC lookup, no buffer write")
    void nonUpdatePathEvent_ignored() throws Exception {
        Message msg = mockMessage(EsqMsgConstants.EVENT_CREATE, "uid-1", 34, "{\"path\":\"1.x.\"}");

        consumer.onEntityBroadcast(msg);

        verify(usersResource, never()).searchByAttributes(anyString(), any(Boolean.class));
        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    @Test
    @DisplayName("X event, KC user exists: no buffer write (URQ owns the update)")
    void updatePath_userExists_noBuffer() throws Exception {
        UserRepresentation existing = new UserRepresentation();
        existing.setId("kc-001");
        when(usersResource.searchByAttributes(
                eq(EsqConstants.JWT_CLAIM_ENTITY_ID + ":uid-1"), eq(true)))
                .thenReturn(List.of(existing));

        Message msg = mockMessage(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, "{\"path\":\"1.20.\"}");

        consumer.onEntityBroadcast(msg);

        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    @Test
    @DisplayName("X event, KC user missing: path buffered for later flush")
    void updatePath_userMissing_buffers() throws Exception {
        when(usersResource.searchByAttributes(
                eq(EsqConstants.JWT_CLAIM_ENTITY_ID + ":uid-1"), eq(true)))
                .thenReturn(Collections.emptyList());

        Message msg = mockMessage(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, "{\"path\":\"1.20.\"}");

        consumer.onEntityBroadcast(msg);

        verify(pathBuffer).store("uid-1", "1.20.");
    }

    @Test
    @DisplayName("X event, textJson has no path: skipped (no buffer write)")
    void updatePath_noPathInJson_skipped() throws Exception {
        // Even though user missing, no path = nothing to buffer.
        Message msg = mockMessage(EsqMsgConstants.EVENT_UPDATE_PATH, "uid-1", 34, "{}");

        consumer.onEntityBroadcast(msg);

        verify(pathBuffer, never()).store(anyString(), anyString());
    }

    private Message mockMessage(String eventType, String entityId, int entityKind, String textJson) throws Exception {
        Message msg = org.mockito.Mockito.mock(Message.class);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenReturn("mid-001");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE)).thenReturn(eventType);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID)).thenReturn(entityId);
        when(msg.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND)).thenReturn(entityKind);
        when(msg.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID)).thenReturn("rid");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID)).thenReturn("cid");
        when(msg.getStringProperty(EsqMsgConstants.FIELD_TEXT)).thenReturn(textJson);
        return msg;
    }
}
