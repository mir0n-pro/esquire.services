/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/27/2025  mir0n extend EsqEntity correctly
 * 01/14/2026 mir0n  email field added
 * 01/18/2026 mir0n  minor touch
 */

package pro.mir0n.esquire.bizTree.dto.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
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
        name = "EsqUsr",
        description = "Holds User entity state, possible custom fields. Extends EsqEntity"
)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class EsqUsr extends EsqEntity {
    public EsqUsr() {
        super();
        children = new HashMap<>();
    }
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

    @Schema(
            description = "Email address", example = "pupkin@example.com"
    )
    private String email;

    @JsonIgnore
    private Map<String, List<String>> children;

    //@JsonIgnore
    @Schema(
            description = "Custom fields, The list is  converting into fields for serialization.",
            example = "CUSTOM_FIELD: 'Sand box example'"
    )
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
        this.email = ((EsqUsrJpa)jpa).getEmail();
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

