/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Schema(
        name = "dictionary",
        description = "Entity dictionary"
)
@Data @Getter @Setter @AllArgsConstructor @NoArgsConstructor
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

};



