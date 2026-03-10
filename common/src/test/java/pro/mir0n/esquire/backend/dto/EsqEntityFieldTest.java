package pro.mir0n.esquire.backend.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EsqEntityFieldTest {

    private EsqEntityField makeField(String type) {
        EsqEntityField ret = new EsqEntityField();
        ret.setType(type);
        return ret;
    }

    // ---- isSubentity ----

    @Test
    @DisplayName("isSubentity() with type 'subentity' → returns true")
    void isSubentity_typeSubentity_returnsTrue() {
        EsqEntityField ret = makeField("subentity");
        assertThat(ret.isSubentity()).isTrue();
    }

    @Test
    @DisplayName("isSubentity() with other type → returns false")
    void isSubentity_otherType_returnsFalse() {
        EsqEntityField ret = makeField("string");
        assertThat(ret.isSubentity()).isFalse();
    }

    // ---- isTabField ----

    @Test
    @DisplayName("isTabField() with type 'tabstring' → returns true")
    void isTabField_tabstring_returnsTrue() {
        EsqEntityField ret = makeField("tabstring");
        assertThat(ret.isTabField()).isTrue();
    }

    @Test
    @DisplayName("isTabField() with type 'tab-ikn-list' → returns true")
    void isTabField_tabIknList_returnsTrue() {
        EsqEntityField ret = makeField("tab-ikn-list");
        assertThat(ret.isTabField()).isTrue();
    }

    @Test
    @DisplayName("isTabField() with type 'tab-iknf-table' → returns true")
    void isTabField_tabIknfTable_returnsTrue() {
        EsqEntityField ret = makeField("tab-iknf-table");
        assertThat(ret.isTabField()).isTrue();
    }

    @Test
    @DisplayName("isTabField() with type 'string' → returns false")
    void isTabField_string_returnsFalse() {
        EsqEntityField ret = makeField("string");
        assertThat(ret.isTabField()).isFalse();
    }

    @Test
    @DisplayName("isTabField() with type 'number' → returns false")
    void isTabField_number_returnsFalse() {
        EsqEntityField ret = makeField("number");
        assertThat(ret.isTabField()).isFalse();
    }
}
