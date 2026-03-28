/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 03/28/2026 mir0n  injectDefaults(Map): populates absent non-nullable fields from dictionary defaults before applyFields
 */

package pro.mir0n.esquire.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//@JacksonXmlRootElement(localName = "Dictionary")
@Data
@Schema(
        name = "EsqEntityLayer",
        description = "Describes an Entity Layer, includes list fields"
)
@JacksonXmlRootElement(localName = "layer")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class EsqEntityLayer {

    @Schema(
            description = "layer (Tab) number"
    )
    @JacksonXmlProperty(localName = "layer")
    private Integer layer;

    @Schema(
            description = "UI title of layer (Tab)"
    )
    @JacksonXmlProperty(localName = "title")
    private String title;

    @Schema(
            description = "List of fields"
    )
    @JacksonXmlElementWrapper(localName = "fields") // Wrapper element for the list
    @JacksonXmlProperty(localName = "field")
    private  List<EsqEntityField> fields;

    public void injectDefaults(Map<String, Object> fieldValues) {
        if (fieldValues == null || fields == null) return;
        for (EsqEntityField f : fields) {
            if ("N".equals(f.getNullable()) && f.getDefaultValue() != null && !fieldValues.containsKey(f.getName())) {
                fieldValues.put(f.getName(), f.getDefaultValue());
            }
        }
    }
};
