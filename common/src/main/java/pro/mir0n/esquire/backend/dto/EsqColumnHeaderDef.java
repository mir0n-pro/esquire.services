/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqColumnHeaderDef",
        description = "Column header definition for Esquire entity list views"
)
@JacksonXmlRootElement(localName = "column-header")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class EsqColumnHeaderDef {

    @Schema(
            description = "column definition", example = "name"
    )
    @JacksonXmlProperty(localName = "columnDef")
    private String columnDef;

    @Schema(
            description = "column definition", example = "Entity Name"
    )
    @JacksonXmlProperty(localName = "header")
    private String header;

}
