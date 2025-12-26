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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.dto.EsqEntityFactory;
import pro.mir0n.esquire.bizTree.dto.EsqNameValue;
import pro.mir0n.esquire.bizTree.jpa.EsqEntityJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.bizTree.jpa.entity.EsqUsrJpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data //xxx:important
@Schema(
        name = "User",
        description = "Schema to hold User information"
)

public class EsqUsr extends EsqEntity {
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
    private String desc;

    @Schema(
            description = "Login Id", example = "pupkin"
    )
    private String loginId;

    @Schema(
            description = "Registration option", example = "individual"
    )
    private String registration;

    @Schema(
            description = "Deleted flag", example = "false"
    )
    private String deleted;

    @JsonIgnore
    private Map<String, List<String>> children = new HashMap<>();

    @JsonIgnore
    private List<EsqNameValue> customFields;

    @JsonAnyGetter
    public Map<String, Object> getAttributesAsFields() {
        // Converts List<Attribute> into a Map for serialization as fields
        Map<String, Object> allFields = new HashMap<>();
        allFields.putAll(children);
        allFields.putAll(customFields.stream()
                .collect(Collectors.toMap(EsqNameValue::getName, EsqNameValue::getValue)));
        return allFields;
    }


   @Override
    protected  void fillDetails(EsqEntityJpa jpa) {
        this.loginId = ((EsqUsrJpa)jpa).getLoginId();
        this.registration = ((EsqUsrJpa)jpa).getRegistration();
        this.deleted = ((EsqUsrJpa)jpa).getDeleted();
    }

    @Override
    protected void fillCustom(List<EsqNameValueJpa> custom) {
        customFields = new ArrayList<>();
        for (EsqNameValueJpa customField : custom) {
            customFields.add(new EsqNameValue(customField.getName(), customField.getValue()));
        }
    }

    private EsqEntityFactory.EsqEntityKind findKind(int kind) {
        int k = (int)Math.floor((double)kind/2) * 2;
        return EsqEntityFactory.EsqEntityKind.getKind(kind);

    }
    @Override
    protected void fillChildren(List<EsqTreeNodeJpa> childNodes) {
        if (childNodes != null) {
            for (EsqTreeNodeJpa node : childNodes) {
                EsqEntityFactory.EsqEntityKind entityKind = findKind(node.getKind());
                String field = entityKind.getPlural();
                List<String> fields = children.get(field);
                if (fields == null) {
                    fields = new ArrayList<>();
                    children.put(field, fields);
                }
                fields.add(node.getName());
            }
        }
    }

}

