package pro.mir0n.esquire.backend.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EsqEntityKindFieldLayerTest {

    private EsqEntityKindFieldLayer makeKfl(EsqEntityField field, String layerTitle) {
        EsqEntityKindFieldLayer ret = new EsqEntityKindFieldLayer();
        ret.setField(field);
        ret.setLayerTitle(layerTitle);
        return ret;
    }

    @Test
    @DisplayName("getLabel() with null field → returns null")
    void getLabel_nullField_returnsNull() {
        EsqEntityKindFieldLayer kfl = makeKfl(null, "Some Layer");
        assertThat(kfl.getLabel()).isNull();
    }

    @Test
    @DisplayName("getLabel() for tab field → returns layerTitle")
    void getLabel_tabField_returnsLayerTitle() {
        EsqEntityField field = new EsqEntityField();
        field.setType("tabstring");
        field.setLabel("Field Label");

        EsqEntityKindFieldLayer kfl = makeKfl(field, "My Layer Title");
        assertThat(kfl.getLabel()).isEqualTo("My Layer Title");
    }

    @Test
    @DisplayName("getLabel() for regular field → returns field label")
    void getLabel_regularField_returnsFieldLabel() {
        EsqEntityField field = new EsqEntityField();
        field.setType("string");
        field.setLabel("Field Label");

        EsqEntityKindFieldLayer kfl = makeKfl(field, "Some Layer");
        assertThat(kfl.getLabel()).isEqualTo("Field Label");
    }
}
