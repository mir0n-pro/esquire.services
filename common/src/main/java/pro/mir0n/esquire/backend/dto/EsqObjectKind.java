/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  address boolean field added
 * 04/06/2026 mir0n  isPathParentOnly(): true for admin kinds (30/32) — ep_path equals parent org path, own PK not appended
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.List;

@Data
@Schema(
        name = "EsqObjectKind",
        description = "Esq Object Kind metadata"
)
@JacksonXmlRootElement(localName = "kind")
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
public class EsqObjectKind {

    @Schema(
            description = "Object Kind ID", example = "1"
    )
    @JacksonXmlProperty(localName = "id")
    private int id =-1;

    @Schema(
            description = "Name of kind, in lower case", example = "system"
    )
    @JacksonXmlProperty(localName = "name")
    private String name;

    @Schema(
            description = "Verbal name of kind", example = "System"
    )
    @JacksonXmlProperty(localName = "title")
    private String title;

    @Schema(
            description = "Plural name, in lower case", example = "systems"
    )
    @JacksonXmlProperty(localName = "plural")
    private String plural = "";


    @Schema(
            description = "Description of kind", example = "Esquire system root"
    )
    @JacksonXmlProperty(localName = "desc")
    private String desc = "";

    @Schema(
            description = "Defines Organization kind", example = "true"
    )
    @JacksonXmlProperty(localName = "org")
    private boolean org = false;

    @Schema(
            description = "Defines User kind", example = "false"
    )
    @JacksonXmlProperty(localName = "usr")
    private boolean usr = false;

    @Schema(
            description = "Defines Account kind", example = "false"
    )
    @JacksonXmlProperty(localName = "acct")
    private boolean acct = false;

    @Schema(
            description = "Name of type icon", example = "img/system.ico"
    )
    @JacksonXmlProperty(localName = "icon")
    private String icon = "";

    @Schema(
            description = "Flags an entity, has details", example = "false"
    )
    @JacksonXmlProperty(localName = "detailed")
    private boolean detailed = false;

    @Schema(
            description = "Flags possible has children listed in details screen", example = "false"
    )
    @JacksonXmlProperty(localName = "childrenDetailed")
    private boolean childrenDetailed = false;

    @Schema(
            description = "Tree flags", example = "BTb"
    )
    @JacksonXmlProperty(localName = "treeFlags")
    private String treeFlags = "";

    @Schema(
            description = "List of heading columns for list view {column name, column header}", example = "[[name,Account ID], [desc,Description]]"
    )
    @JacksonXmlElementWrapper(localName = "listHeaders")
    @JacksonXmlProperty(localName = "column-header")
    private  List<EsqColumnHeaderDef> listHeaders = null;

    @Schema(
            description = "List of entity kinds can be created within the entity", example = "[20]"
    )
    @JacksonXmlElementWrapper(localName = "childKinds") // Wrapper element for the list
    @JacksonXmlProperty(localName = "kind")
    private List<Integer> childKinds = null;

    @Schema(
            description = "List of entity commands", example = "[move,key]"
    )
    @JacksonXmlElementWrapper(localName = "commands") // Wrapper element for the list
    @JacksonXmlProperty(localName = "command")
    private List<String> commands = null;

    @JacksonXmlProperty(localName = "address")
    private boolean address = false;

    /**
     * Returns true when this entity's ep_path equals its parent's path — own PK is NOT appended.
     * Applies to SYS_ADMIN (30) and ADMIN (32): their visibility root is the org they belong to.
     * Regular users (CLIENT/MERCHANT 34/36) and ORGs return false — their ep_path includes own PK.
     */
    public boolean isPathParentOnly() {
        //xxx: hardcoded for now
        return isAcct()
            || id == 30 //SYS_ADMIN
            || id == 32; //ADMIN
    }

}
