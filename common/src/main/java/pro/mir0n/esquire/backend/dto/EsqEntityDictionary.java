/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/01/2026 mir0n removed @AllArgsConstructor
 * 03/06/2026 mir0n findField() and fillКindFieldLayer() methods added; @Slf4j added
 * 03/08/2026 mir0n  fillКindFieldLayer(): setLayerTitle() called to populate layer title context
 * 03/10/2026 mir0n  fillКindFieldLayer() renamed fillKindFieldLayer() — Cyrillic К replaced with ASCII K
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.extern.slf4j.Slf4j;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Schema(
        name = "dictionary",
        description = "Entity dictionary"
)
@Slf4j
@Data @Getter @Setter @NoArgsConstructor
@JacksonXmlRootElement(localName = "dictionary")
@JsonIgnoreProperties({"completed"})
public class EsqEntityDictionary {
    // not transportable

    private boolean completed = false;

    @Schema(
            description = "Entity Kind", example = "1"
    )
    private Integer kind = 0;

    @Schema(
            description = "List of layers"
    )
    @JacksonXmlElementWrapper(localName = "layers") // Wrapper element for the list
    @JacksonXmlProperty(localName = "layer")
    private List<EsqEntityLayer> layers = new ArrayList<>();

    public void sortLayers() {
         layers.sort(Comparator.comparing(EsqEntityLayer::getLayer));
     }

    public EsqEntityLayer findLayer(Integer id) {
        EsqEntityLayer ret = null;
        for (EsqEntityLayer layer : layers) {
            if (layer.getLayer().equals(id)) {
                ret =  layer;
                break;
            }
        }
        return ret;
    }
    public EsqEntityField findField( String name) {
        EsqEntityField ret = null;
        for (EsqEntityLayer layer : getLayers()) {
            if (layer.getFields() != null) {
                for (EsqEntityField f : layer.getFields()) {
                    if (name.equals(f.getName())) {
                        ret = f;
                        break;
                    }
                }
            }
            if (ret != null) {
                break;
            }
        }
        return ret;
    }

    public EsqEntityKindFieldLayer fillKindFieldLayer(String name,EsqEntityKindFieldLayer given) {
        EsqEntityKindFieldLayer ret = given;
        if (ret == null) {
            ret = new EsqEntityKindFieldLayer();
        } else {
            //given.setEntityKind(0);
            given.setField(null);
            //given.setLayer(0);
        }
        for (EsqEntityLayer layer : getLayers()) {
            if (layer.getFields() != null) {
                for (EsqEntityField f : layer.getFields()) {
                    if (name.equals(f.getName())) {
                        ret.setEntityKind(getKind());
                        ret.setLayer(layer.getLayer());
                        ret.setField(f);
                        ret.setLayerTitle(layer.getTitle());
                        break;
                    }
                }
            }
            if (ret.getField() != null) {
                break;
            }
        }
        return ret;
    }
}



