package pro.mir0n.esquire.backend.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EsqEntityDictionaryTest {

    private EsqEntityDictionary dict;

    // ---- helpers ----

    private EsqEntityField makeField(String name, String label) {
        EsqEntityField ret = new EsqEntityField();
        ret.setName(name);
        ret.setLabel(label);
        ret.setType("string");
        ret.setNullable("Y");
        return ret;
    }

    private EsqEntityLayer makeLayer(int layerNum, String title, List<EsqEntityField> fields) {
        EsqEntityLayer ret = new EsqEntityLayer();
        ret.setLayer(layerNum);
        ret.setTitle(title);
        ret.setFields(fields);
        return ret;
    }

    @BeforeEach
    void setUp() {
        dict = new EsqEntityDictionary();
        dict.setKind(5);

        EsqEntityField f1 = makeField("firstName", "First Name");
        EsqEntityField f2 = makeField("lastName", "Last Name");
        EsqEntityField f3 = makeField("email", "Email");

        EsqEntityLayer layer1 = makeLayer(2, "Basic Info", List.of(f1, f2));
        EsqEntityLayer layer2 = makeLayer(1, "Contact", List.of(f3));

        dict.getLayers().add(layer1);
        dict.getLayers().add(layer2);
    }

    // ---- sortLayers ----

    @Test
    @DisplayName("sortLayers → layers sorted ascending by layer number")
    void sortLayers_sortsAscending() {
        dict.sortLayers();
        List<EsqEntityLayer> layers = dict.getLayers();
        assertThat(layers.get(0).getLayer()).isEqualTo(1);
        assertThat(layers.get(1).getLayer()).isEqualTo(2);
    }

    // ---- findLayer ----

    @Test
    @DisplayName("findLayer with existing id → returns layer")
    void findLayer_found_returnsLayer() {
        EsqEntityLayer ret = dict.findLayer(2);
        assertThat(ret).isNotNull();
        assertThat(ret.getLayer()).isEqualTo(2);
        assertThat(ret.getTitle()).isEqualTo("Basic Info");
    }

    @Test
    @DisplayName("findLayer with non-existent id → returns null")
    void findLayer_notFound_returnsNull() {
        EsqEntityLayer ret = dict.findLayer(99);
        assertThat(ret).isNull();
    }

    // ---- findField ----

    @Test
    @DisplayName("findField in first layer → returns field")
    void findField_inFirstLayer_returnsField() {
        EsqEntityField ret = dict.findField("firstName");
        assertThat(ret).isNotNull();
        assertThat(ret.getName()).isEqualTo("firstName");
    }

    @Test
    @DisplayName("findField in middle/second layer → returns field")
    void findField_inMiddleLayer_returnsField() {
        EsqEntityField ret = dict.findField("email");
        assertThat(ret).isNotNull();
        assertThat(ret.getName()).isEqualTo("email");
    }

    @Test
    @DisplayName("findField with non-existent name → returns null")
    void findField_notFound_returnsNull() {
        EsqEntityField ret = dict.findField("nonExistent");
        assertThat(ret).isNull();
    }

    @Test
    @DisplayName("findField skips layer with null fields list")
    void findField_layerHasNullFields_skipsLayer() {
        EsqEntityLayer nullFieldsLayer = makeLayer(3, "Empty Layer", null);
        dict.getLayers().add(nullFieldsLayer);

        EsqEntityField ret = dict.findField("email");
        assertThat(ret).isNotNull();
        assertThat(ret.getName()).isEqualTo("email");
    }

    // ---- fillKindFieldLayer ----

    @Test
    @DisplayName("fillKindFieldLayer with null given → creates new KFL")
    void fillKindFieldLayer_nullGiven_createsNew() {
        EsqEntityKindFieldLayer ret = dict.fillKindFieldLayer("firstName", null);
        assertThat(ret).isNotNull();
    }

    @Test
    @DisplayName("fillKindFieldLayer with non-null given → reuses the instance")
    void fillKindFieldLayer_nonNullGiven_reuses() {
        EsqEntityKindFieldLayer existing = new EsqEntityKindFieldLayer();
        EsqEntityKindFieldLayer ret = dict.fillKindFieldLayer("firstName", existing);
        assertThat(ret).isSameAs(existing);
    }

    @Test
    @DisplayName("fillKindFieldLayer → populates entityKind, layer, field, layerTitle")
    void fillKindFieldLayer_populatesAllFields() {
        EsqEntityKindFieldLayer ret = dict.fillKindFieldLayer("firstName", null);
        assertThat(ret.getEntityKind()).isEqualTo(5);
        assertThat(ret.getLayer()).isEqualTo(2);
        assertThat(ret.getField()).isNotNull();
        assertThat(ret.getField().getName()).isEqualTo("firstName");
        assertThat(ret.getLayerTitle()).isEqualTo("Basic Info");
    }

    @Test
    @DisplayName("fillKindFieldLayer for non-existent field → returns KFL with null field")
    void fillKindFieldLayer_fieldNotFound_returnsWithNullField() {
        EsqEntityKindFieldLayer ret = dict.fillKindFieldLayer("notExisting", null);
        assertThat(ret).isNotNull();
        assertThat(ret.getField()).isNull();
    }
}
