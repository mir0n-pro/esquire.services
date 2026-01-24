/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 01/14/2026 mir0n  "personal" field added
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Data
@Schema(
        name = "EsqEntityField",
        description = "Entity Field metadata"
)
@JacksonXmlRootElement(localName = "field")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class EsqEntityField {

    @Schema(
            description = "Field name"
    )
    @JacksonXmlProperty(localName = "name")
    private String name;

    @Schema(
            description = "Sorting number, position order on UI layer(tab)"
    )
    @JacksonXmlProperty(localName = "sort")
    private Integer sort;

    @Schema(
            description = "UI field label"
    )
    @JacksonXmlProperty(localName = "label")
    private String label;

    @Schema(
            description = "UI field value type"
    )
    @JacksonXmlProperty(localName = "type")
    private String type;

    @Schema(
            description = "Tooltip"
    )
    @JacksonXmlProperty(localName = "tooltip")
    private String tooltip;

    @Schema(
            description = "List values"
    )
    @JacksonXmlElementWrapper(localName = "listvalues") // Wrapper element for the list
    @JacksonXmlProperty(localName = "value")
    private List<String> listvalues;

    @Schema(
            description = "Nullable"
    )
    @JacksonXmlProperty(localName = "nullable")
    private Boolean nullable;

    @Schema(
            description = "nullmeaning"
    )
    @JacksonXmlProperty(localName = "nullmeaning")
    private String nullmeaning;

    @Schema(
            description = "validation"
    )
    @JacksonXmlProperty(localName = "validation")
    private String validation;

    @Schema(
            description = "readwrite"
    )
    @JacksonXmlProperty(localName = "readwrite")
    private Integer readwrite;

    @Schema(
            description = "format"
    )
    @JacksonXmlProperty(localName = "format")
    private String format;

    @Schema(
            description = "personal"
    )
    @JacksonXmlProperty(localName = "personal")
    private String personal;
};
