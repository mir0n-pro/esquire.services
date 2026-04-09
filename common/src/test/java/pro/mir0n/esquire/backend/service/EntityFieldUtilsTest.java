package pro.mir0n.esquire.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EntityFieldUtilsTest {

    private EsqEntityLayer layer;

    private EsqEntityField field(String name, String nullable, String defaultValue) {
        EsqEntityField ret = new EsqEntityField();
        ret.setName(name);
        ret.setNullable(nullable);
        ret.setDefaultValue(defaultValue);
        return ret;
    }

    @BeforeEach
    void setUp() {
        layer = new EsqEntityLayer();
        layer.setLayer(1);
        layer.setTitle("Generic");
        layer.setFields(new ArrayList<>());
    }

    // ---- enforceDefaults: non-nullable field, null on entity → default applied ----

    @Test
    @DisplayName("enforceDefaults: non-nullable field, null on entity → default applied")
    void enforceDefaults_nonNullable_nullOnEntity_appliesDefault() {
        layer.getFields().add(field("status", "N", "O"));
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setStatus(null);

        EntityFieldUtils.enforceDefaults(layer, acct);

        assertThat(acct.getStatus()).isEqualTo("O");
    }

    // ---- enforceDefaults: non-nullable field, already set → not overwritten ----

    @Test
    @DisplayName("enforceDefaults: non-nullable field, already set → not overwritten")
    void enforceDefaults_nonNullable_alreadySet_doesNotOverwrite() {
        layer.getFields().add(field("status", "N", "O"));
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setStatus("C");

        EntityFieldUtils.enforceDefaults(layer, acct);

        assertThat(acct.getStatus()).isEqualTo("C");
    }

    // ---- enforceDefaults: nullable field, null on entity → not touched ----

    @Test
    @DisplayName("enforceDefaults: nullable field, null on entity → not touched")
    void enforceDefaults_nullable_nullOnEntity_doesNotApply() {
        layer.getFields().add(field("ccy", "Y", "USD"));
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setCcy(null);

        EntityFieldUtils.enforceDefaults(layer, acct);

        assertThat(acct.getCcy()).isNull();
    }

    // ---- enforceDefaults: non-nullable, no defaultValue → not touched ----

    @Test
    @DisplayName("enforceDefaults: non-nullable field with no defaultValue → not touched")
    void enforceDefaults_nonNullable_noDefault_doesNotApply() {
        layer.getFields().add(field("ccy", "N", null));
        EsqAcctJpa acct = new EsqAcctJpa();
        acct.setCcy(null);

        EntityFieldUtils.enforceDefaults(layer, acct);

        assertThat(acct.getCcy()).isNull();
    }

    // ---- enforceDefaults: null layer → no exception ----

    @Test
    @DisplayName("enforceDefaults: null layer → no exception")
    void enforceDefaults_nullLayer_noException() {
        EsqAcctJpa acct = new EsqAcctJpa();
        assertThatCode(() -> EntityFieldUtils.enforceDefaults(null, acct)).doesNotThrowAnyException();
    }

    // ---- enforceDefaults: null entity → no exception ----

    @Test
    @DisplayName("enforceDefaults: null entity → no exception")
    void enforceDefaults_nullEntity_noException() {
        layer.getFields().add(field("status", "N", "O"));
        assertThatCode(() -> EntityFieldUtils.enforceDefaults(layer, null)).doesNotThrowAnyException();
    }

    // ---- enforceDefaults: unknown field name (no matching property) → silently skipped ----

    @Test
    @DisplayName("enforceDefaults: unknown field name on entity → silently skipped")
    void enforceDefaults_unknownField_silentlySkipped() {
        layer.getFields().add(field("nonExistentField", "N", "X"));
        EsqAcctJpa acct = new EsqAcctJpa();
        assertThatCode(() -> EntityFieldUtils.enforceDefaults(layer, acct)).doesNotThrowAnyException();
    }
}
