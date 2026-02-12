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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;

@Schema(
        name = "EsqObjectKinds",
        description = "List of Object Kinds"
)
@Data @Getter @Setter
@JacksonXmlRootElement(localName = "object-kinds")
public class EsqObjectKinds {
    @Schema(
            description = "List of object kinds"
    )

    @JacksonXmlElementWrapper(localName = "kinds") // Wrapper element for the list
    @JacksonXmlProperty(localName = "kind")
    private List<EsqObjectKind> kinds = new ArrayList<>();

};



