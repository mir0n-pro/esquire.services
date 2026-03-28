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
 * 02/19/2026 mir0n  nullable type changed from Boolean to String
 *                   minmax field added
 * 03/08/2026 mir0n  isSubentity() and isTabField() helper methods added
 * 03/20/2026 mir0n  affects3 field added
 * 03/28/2026 mir0n  defaultValue field added; @JsonProperty("default") for correct JSON serialization
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private String nullable;

    @Schema(
            description = "default value for required (non-nullable) fields"
    )
    @JsonProperty("default")
    @JacksonXmlProperty(localName = "default")
    private String defaultValue;

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

    @Schema(
            description = "minmax: min/max values for numeric fields, max size for string and tab-string"
    )
    @JacksonXmlProperty(localName = "minmax")
    private String minmax;

    @Schema(
            description = "Affects the tree flag"
    )
    @JacksonXmlProperty(localName = "affects3")
    private String affects3;

    public boolean isSubentity() {
        return "subentity".equals(type);
    }

    public boolean isTabField() {
        boolean ret = false;
        switch (type) {
        case "tabstring":
        case "tab-ikn-list":
        case "tab-iknf-table":
            ret = true;
            break;
        default:
            break;
        }
        return ret;
    }

};
