package pro.mir0n.esquire.backend.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.error.InvalidValueException;

import static org.assertj.core.api.Assertions.*;

class GenericValidatorTest {

    private GenericValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GenericValidator();
    }

    // ---- helper ----

    private EsqEntityKindFieldLayer makeKfl(String type, String nullable, String personal,
                                            String pattern, String minmax) {
        EsqEntityField field = new EsqEntityField();
        field.setName("testField");
        field.setLabel("Test Field");
        field.setType(type);
        field.setNullable(nullable);
        field.setPersonal(personal);
        field.setValidation(pattern);
        field.setMinmax(minmax);

        EsqEntityKindFieldLayer ret = new EsqEntityKindFieldLayer();
        ret.setEntityKind(1);
        ret.setLayer(1);
        ret.setLayerTitle("Test Layer");
        ret.setField(field);
        return ret;
    }

    // ---- personal flag ----

    @Test
    @DisplayName("personal=true, field.personal=N → throws InvalidValueException")
    void personalBlocked_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", "N", null, null);
        assertThatThrownBy(() -> validator.validate(null, kfl, true, "someValue"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("personal=true, field.personal=Y → returns value")
    void personalAllowed_returnsValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", "Y", null, null);
        Object ret = validator.validate(null, kfl, true, "ok");
        assertThat(ret).isEqualTo("ok");
    }

    // ---- blank / whitespace conversion ----

    @Test
    @DisplayName("blank string → converted to null")
    void blankString_convertedToNull() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "");
        assertThat(ret).isNull();
    }

    @Test
    @DisplayName("whitespace string → converted to null")
    void whitespaceString_convertedToNull() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "   ");
        assertThat(ret).isNull();
    }

    // ---- null / nullable ----

    @Test
    @DisplayName("null on nullable field → returns null")
    void nullOnNullableField_returnsNull() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, null);
        assertThat(ret).isNull();
    }

    @Test
    @DisplayName("null on required field (nullable=N) → throws InvalidValueException")
    void nullOnRequiredField_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "N", null, null, null);
        assertThatThrownBy(() -> validator.validate(null, kfl, false, null))
                .isInstanceOf(InvalidValueException.class);
    }

    // ---- number ----

    @Test
    @DisplayName("number in range → returns value")
    void numberInRange_returnsValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("number", "Y", null, null, "1,10");
        Object ret = validator.validate(null, kfl, false, "5");
        assertThat(ret).isEqualTo("5");
    }

    @Test
    @DisplayName("number below min → throws InvalidValueException")
    void numberBelowMin_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("number", "Y", null, null, "1,10");
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "0"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("number above max → throws InvalidValueException")
    void numberAboveMax_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("number", "Y", null, null, "1,10");
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "11"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("non-numeric value for number field → throws InvalidValueException")
    void numberNonNumeric_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("number", "Y", null, null, "1,10");
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "abc"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("number with no minmax → accepted without range check")
    void numberNoMinmax_accepted() {
        EsqEntityKindFieldLayer kfl = makeKfl("number", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "999");
        assertThat(ret).isEqualTo("999");
    }

    // ---- string with pattern ----

    @Test
    @DisplayName("string matching pattern → returns value")
    void stringMatchesPattern_returnsValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, "^[a-z]+$", null);
        Object ret = validator.validate(null, kfl, false, "hello");
        assertThat(ret).isEqualTo("hello");
    }

    @Test
    @DisplayName("string not matching pattern → throws InvalidValueException")
    void stringPatternMismatch_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, "^[a-z]+$", null);
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "HELLO"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("string longer than maxLen → truncated to maxLen")
    void stringTruncatedToMaxLen() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, null, "5");
        Object ret = validator.validate(null, kfl, false, "abcdefgh");
        assertThat(ret).isEqualTo("abcde");
    }

    @Test
    @DisplayName("string with null pattern → passes through")
    void stringNullPattern_accepted() {
        EsqEntityKindFieldLayer kfl = makeKfl("string", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "anything");
        assertThat(ret).isEqualTo("anything");
    }

    // ---- flag ----

    @Test
    @DisplayName("flag value Y → valid")
    void flagY_valid() {
        EsqEntityKindFieldLayer kfl = makeKfl("flag", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "Y");
        assertThat(ret).isEqualTo("Y");
    }

    @Test
    @DisplayName("flag value N → valid")
    void flagN_valid() {
        EsqEntityKindFieldLayer kfl = makeKfl("flag", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "N");
        assertThat(ret).isEqualTo("N");
    }

    @Test
    @DisplayName("flag value other than Y/N → throws InvalidValueException")
    void flagInvalid_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("flag", "Y", null, null, null);
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "X"))
                .isInstanceOf(InvalidValueException.class);
    }

    // ---- date ----

    @Test
    @DisplayName("date with valid ISO format → returns value")
    void dateValidFormat_returnsValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("date", "Y", null, null, null);
        Object ret = validator.validate(null, kfl, false, "2026-03-10");
        assertThat(ret).isEqualTo("2026-03-10");
    }

    @Test
    @DisplayName("date with invalid format → throws InvalidValueException")
    void dateInvalidFormat_throwsInvalidValue() {
        EsqEntityKindFieldLayer kfl = makeKfl("date", "Y", null, null, null);
        assertThatThrownBy(() -> validator.validate(null, kfl, false, "10/03/2026"))
                .isInstanceOf(InvalidValueException.class);
    }
}
