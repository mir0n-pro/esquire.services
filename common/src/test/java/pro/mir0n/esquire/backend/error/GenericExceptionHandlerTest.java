package pro.mir0n.esquire.backend.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenericExceptionHandlerTest {

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("InvalidValueException → returns 400 BAD_REQUEST")
    void invalidValueException_returns400() {
        InvalidValueException ex = new InvalidValueException("bad value", "field", "Field Label", "0");

        ResponseEntity<ProblemDetail> ret =
                GenericExceptionHandler.handleGenericRuntimeException(ex, request);

        assertThat(ret.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("ResourceNotFoundException → returns 404 NOT_FOUND")
    void resourceNotFoundException_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", "99");

        ResponseEntity<ProblemDetail> ret =
                GenericExceptionHandler.handleGenericRuntimeException(ex, request);

        assertThat(ret.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("CommandNotAcceptedException -> returns 503 SERVICE_UNAVAILABLE")
    void commandNotAcceptedException_returns503() {
        CommandNotAcceptedException ex = new CommandNotAcceptedException("move");

        ResponseEntity<ProblemDetail> ret =
                GenericExceptionHandler.handleGenericRuntimeException(ex, request);

        assertThat(ret.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("PermissionDeniedException → returns 403 FORBIDDEN")
    void permissionDeniedException_returns403() {
        PermissionDeniedException ex = new PermissionDeniedException("User", "update");

        ResponseEntity<ProblemDetail> ret =
                GenericExceptionHandler.handleGenericRuntimeException(ex, request);

        assertThat(ret.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
