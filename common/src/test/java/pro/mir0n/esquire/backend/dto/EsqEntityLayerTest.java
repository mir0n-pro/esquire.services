package pro.mir0n.esquire.backend.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EsqEntityLayerTest {

    private EsqEntityLayer layer;

    // ---- helpers ----

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

    // ---- injectDefaults: non-nullable, has default, absent → injected ----

    @Test
    @DisplayName("injectDefaults: non-nullable field with default, absent → injected")
    void injectDefaults_nonNullableWithDefault_absent_injectsValue() {
        layer.getFields().add(field("deleted", "N", "N"));
        Map<String, Object> map = new HashMap<>();

        layer.injectDefaults(map);

        assertThat(map).containsEntry("deleted", "N");
    }

    // ---- injectDefaults: non-nullable, has default, already present → not overridden ----

    @Test
    @DisplayName("injectDefaults: non-nullable field with default, already present → not overridden")
    void injectDefaults_nonNullableWithDefault_present_doesNotOverride() {
        layer.getFields().add(field("deleted", "N", "N"));
        Map<String, Object> map = new HashMap<>();
        map.put("deleted", "Y");

        layer.injectDefaults(map);

        assertThat(map).containsEntry("deleted", "Y");
    }

    // ---- injectDefaults: nullable, has default, absent → not injected ----

    @Test
    @DisplayName("injectDefaults: nullable field with default, absent → not injected")
    void injectDefaults_nullableWithDefault_absent_doesNotInject() {
        layer.getFields().add(field("desc", "Y", "some default"));
        Map<String, Object> map = new HashMap<>();

        layer.injectDefaults(map);

        assertThat(map).doesNotContainKey("desc");
    }

    // ---- injectDefaults: non-nullable, no default, absent → not injected ----

    @Test
    @DisplayName("injectDefaults: non-nullable field with no default, absent → not injected")
    void injectDefaults_nonNullableNoDefault_absent_doesNotInject() {
        layer.getFields().add(field("email", "N", null));
        Map<String, Object> map = new HashMap<>();

        layer.injectDefaults(map);

        assertThat(map).doesNotContainKey("email");
    }

    // ---- injectDefaults: null fieldValues map → no exception ----

    @Test
    @DisplayName("injectDefaults: null fieldValues map → no exception")
    void injectDefaults_nullFieldValues_noException() {
        layer.getFields().add(field("deleted", "N", "N"));
        assertThatCode(() -> layer.injectDefaults(null)).doesNotThrowAnyException();
    }

    // ---- injectDefaults: null fields list → no exception ----

    @Test
    @DisplayName("injectDefaults: null fields list → no exception")
    void injectDefaults_nullFieldsList_noException() {
        layer.setFields(null);
        assertThatCode(() -> layer.injectDefaults(new HashMap<>())).doesNotThrowAnyException();
    }

    // ---- injectDefaults: multiple fields, selective injection ----

    @Test
    @DisplayName("injectDefaults: multiple fields — only absent non-nullable fields with default are injected")
    void injectDefaults_multipleFields_selectiveInjection() {
        layer.getFields().add(field("deleted", "N", "N"));          // absent, non-nullable, has default → inject
        layer.getFields().add(field("status",  "N", "O"));          // present in map → keep existing value
        layer.getFields().add(field("desc",    "Y", "some desc"));  // nullable → skip
        layer.getFields().add(field("name",    "N", null));         // no default → skip

        Map<String, Object> map = new HashMap<>();
        map.put("status", "C");

        layer.injectDefaults(map);

        assertThat(map).containsEntry("deleted", "N");
        assertThat(map).containsEntry("status", "C");
        assertThat(map).doesNotContainKey("desc");
        assertThat(map).doesNotContainKey("name");
    }
}
