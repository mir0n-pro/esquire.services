/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.dto.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.dto.EsqNameValue;
import pro.mir0n.esquire.bizTree.jpa.EsqEntityJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.bizTree.jpa.entity.EsqOrgJpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data //xxx:important
@Schema(
        name = "Account",
        description = "Schema to holdGeneric Entity information"
)

public class EsqOrg extends EsqEntity {
    @Schema(
            description = "Entity ID", example = ""
    )
    private String id;

    @Schema(
            description = "Type of entity", example = "1 for system"
    )
    private Integer kind;

    @Schema(
            description = "Entity name", example = "System"
    )
    private String name;

    @Schema(
            description = "Entity description", example = "Entity description"
    )
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String desc;

    @Schema(
            description = "Organization full name", example = "xxx"
    )
    private String fullName;

//    @Schema(
//            description = "Custom fields", example = ""
//    )

//    @JsonSerialize(using = EsqNameValueSerializer.class)
    //    @JsonUnwrapped // Ensures fields are not nested under an "attributes" key
    @JsonIgnore
    private List<EsqNameValue> customFields;

    @JsonAnyGetter
    public Map<String, String> getAttributesAsFields() {
        // Converts List<Attribute> into a Map for serialization as fields
        return customFields.stream()
                .collect(Collectors.toMap(EsqNameValue::getName, EsqNameValue::getValue));
    }

    @Override
    protected  void fillDetails(EsqEntityJpa jpa) {
       this.fullName = ((EsqOrgJpa)jpa).getFullName();
    }

    @Override
    protected void fillCustom(List<EsqNameValueJpa> custom) {
        customFields = new ArrayList<>();
        for (EsqNameValueJpa customField : custom) {
            customFields.add(new EsqNameValue(customField.getName(), customField.getValue()));
        }
    }
    @Override
    protected void fillChildren(List<EsqTreeNodeJpa> children) {}

}

