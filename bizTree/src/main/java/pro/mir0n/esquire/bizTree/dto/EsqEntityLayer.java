/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;

//@JacksonXmlRootElement(localName = "Dictionary")
@Data
@Schema(
        name = "layer",
        description = "Entity fields layer description"
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
};
