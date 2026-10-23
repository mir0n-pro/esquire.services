package pro.mir0n.esquire.pacMan.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.service.PacManCommands;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacManControllerTest {

    @Mock
    private PacManCommands commands;

    private PacManController controller;

    @BeforeEach
    void setUp() {
        controller = new PacManController(commands);
    }

    @Test
    @DisplayName("esquireCommand: delegates to the commands, returns 200 with the entity")
    void esquireCommand_delegates_returnsOk() {
        EsqEntity entity = mock(EsqEntity.class);
        when(commands.esquireCommand(50, "10", "details")).thenReturn(entity);

        ResponseEntity<EsqEntity> response = controller.esquireCommand(50, "10", "details");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(entity);
    }

    @Test
    @DisplayName("esquireCommandSave: delegates to the commands, returns 200 with the entity")
    void esquireCommandSave_delegates_returnsOk() {
        EsqEntity entity = mock(EsqEntity.class);
        Map<String, Object> fields = Map.of("name", "ACME");
        when(commands.esquireCommandSave(50, "10", "save", fields)).thenReturn(entity);

        ResponseEntity<EsqEntity> response = controller.esquireCommandSave(50, "10", "save", fields);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(entity);
    }

    @Test
    @DisplayName("esquireCommandDelete: delegates to the commands, returns 200")
    void esquireCommandDelete_delegates_returnsOk() {
        ResponseEntity<Void> response = controller.esquireCommandDelete(50, "10", "delete");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(commands).esquireCommandDelete(50, "10", "delete");
    }

    @Test
    @DisplayName("esquireCommandAcct: delegates to the commands, returns 200 with the transaction")
    void esquireCommandAcct_delegates_returnsOk() {
        AcctTransactionSingle tx = new AcctTransactionSingle();
        Map<String, Object> fields = Map.of("typeId", 1000, "amount", 10);
        when(commands.esquireCommandAcct(50, "10", "acct", fields)).thenReturn(tx);

        ResponseEntity<AcctTransactionSingle> response = controller.esquireCommandAcct(50, "10", "acct", fields);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(tx);
    }
}
