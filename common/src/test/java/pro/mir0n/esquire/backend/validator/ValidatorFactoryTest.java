package pro.mir0n.esquire.backend.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.error.DeleteRestrictedException;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ValidatorFactoryTest {

    private ValidatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = ValidatorFactory.getInstance();
        factory.init(null);
    }

    // ---- helper ----

    private EsqEntityKindFieldLayer makeKfl(int entityKind, String type) {
        EsqEntityField field = new EsqEntityField();
        field.setName("f");
        field.setLabel("F");
        field.setType(type);
        field.setNullable("Y");

        EsqEntityKindFieldLayer ret = new EsqEntityKindFieldLayer();
        ret.setEntityKind(entityKind);
        ret.setLayer(1);
        ret.setLayerTitle("Layer");
        ret.setField(field);
        return ret;
    }

    @Test
    @DisplayName("no biz validators → only generic result returned")
    void validate_noBizValidators_returnsGenericResult() {
        EsqEntityKindFieldLayer kfl = makeKfl(1, "string");
        Object ret = factory.validate(null, kfl, false, "hello");
        assertThat(ret).isEqualTo("hello");
    }

    @Test
    @DisplayName("biz validator registered for same kind → chain called, biz result returned")
    void validate_bizValidatorForKind_chainsCalled() {
        IValidator biz = Mockito.mock(IValidator.class);
        when(biz.validate(any(), any(), eq(false), eq("hello"))).thenReturn("biz-result");

        factory.init(Map.of(1, biz));
        EsqEntityKindFieldLayer kfl = makeKfl(1, "string");

        Object ret = factory.validate(null, kfl, false, "hello");

        assertThat(ret).isEqualTo("biz-result");
        verify(biz, times(1)).validate(any(), eq(kfl), eq(false), eq("hello"));
    }

    @Test
    @DisplayName("biz validator for different kind → only generic called")
    void validate_bizValidatorForDifferentKind_onlyGenericCalled() {
        IValidator biz = Mockito.mock(IValidator.class);
        factory.init(Map.of(99, biz));

        EsqEntityKindFieldLayer kfl = makeKfl(1, "string");
        Object ret = factory.validate(null, kfl, false, "hello");

        assertThat(ret).isEqualTo("hello");
        verifyNoInteractions(biz);
    }

    @Test
    @DisplayName("generic validator throws → exception propagates")
    void validate_genericThrows_propagates() {
        factory.init(null);
        EsqEntityKindFieldLayer kfl = makeKfl(1, "flag");
        assertThatThrownBy(() -> factory.validate(null, kfl, false, "X"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("biz validator throws → exception propagates")
    void validate_bizThrows_propagates() {
        IValidator biz = Mockito.mock(IValidator.class);
        when(biz.validate(any(), any(), anyBoolean(), any()))
                .thenThrow(new InvalidValueException("biz error", "f", "F", "0"));

        factory.init(Map.of(1, biz));
        EsqEntityKindFieldLayer kfl = makeKfl(1, "string");

        assertThatThrownBy(() -> factory.validate(null, kfl, false, "hello"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("biz error");
    }

    // ---- system entity flag (anti-deletion) ----

    private EsqEntityJpa makeEntity(int kind, String systemFlg) {
        EsqEntityJpa entity = new EsqEntityJpa();
        entity.setId("1");
        entity.setKind(kind);
        entity.setSystemFlg(systemFlg);
        return entity;
    }

    @Test
    @DisplayName("validateDelete: system entity (systemFlg='Y') is protected → DeleteRestrictedException")
    void validateDelete_systemEntity_throws() {
        factory.init(null);
        assertThatThrownBy(() -> factory.validateDelete(makeEntity(20, "Y")))
                .isInstanceOf(DeleteRestrictedException.class);
    }

    @Test
    @DisplayName("validateDelete: non-system entity (systemFlg='N') is not protected → no throw")
    void validateDelete_nonSystemEntity_passes() {
        factory.init(null);
        assertThatCode(() -> factory.validateDelete(makeEntity(20, "N")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDelete: unset systemFlg (null) is not protected → no throw")
    void validateDelete_nullSystemFlg_passes() {
        factory.init(null);
        assertThatCode(() -> factory.validateDelete(makeEntity(20, null)))
                .doesNotThrowAnyException();
    }
}
