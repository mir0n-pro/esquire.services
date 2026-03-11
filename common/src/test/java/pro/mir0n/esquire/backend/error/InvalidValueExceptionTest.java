package pro.mir0n.esquire.backend.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InvalidValueExceptionTest {

    @Test
    @DisplayName("single-field constructor → errors list has exactly one entry")
    void singleFieldConstructor_errorsListHasOneEntry() {
        InvalidValueException ex = new InvalidValueException("value is required", "email", "Email", "0");
        assertThat(ex.errors).hasSize(1);
    }

    @Test
    @DisplayName("single-field constructor → message contains fieldLabel and message text")
    void singleFieldConstructor_messageFormatCorrect() {
        InvalidValueException ex = new InvalidValueException("value is required", "email", "Email", "0");
        assertThat(ex.getMessage()).contains("Email");
        assertThat(ex.getMessage()).contains("value is required");
    }

    @Test
    @DisplayName("multi-error constructor → errors list preserved as-is")
    void multipleErrorsConstructor_errorsListPreserved() {
        List<Map<String, String>> errors = List.of(
                Map.of("fieldName", "email", "message", "required"),
                Map.of("fieldName", "name", "message", "too long")
        );
        InvalidValueException ex = new InvalidValueException("Multiple validation errors", errors);

        assertThat(ex.errors).hasSize(2);
        assertThat(ex.getMessage()).isEqualTo("Multiple validation errors");
    }
}
