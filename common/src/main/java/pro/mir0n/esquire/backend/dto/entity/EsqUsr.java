/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/27/2025  mir0n extend EsqEntity correctly
 * 01/14/2026 mir0n  email field added
 * 01/18/2026 mir0n  minor touch
 * 02/01/2026 mir0n List<String>> children replaced with List<EsqEntity>
 *                  "accounts" field does not require custom JsonAnyGetter anymore
 */

package pro.mir0n.esquire.backend.dto.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqEntityFactory;
import pro.mir0n.esquire.backend.dto.EsqNameValue;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;

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
        accounts = new ArrayList<>();
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

    @Schema(
            description = "List of accounts belonging to user"
    )
    private List<EsqEntity> accounts;

    @JsonIgnore
    private List<EsqNameValue> customFields;

    @JsonAnyGetter
    public Map<String, Object> getAttributesAsFields() {
        // Converts List<Attribute> into a Map for serialization as fields
        Map<String, Object> allFields = new HashMap<>();
        if(customFields != null) {
            allFields.putAll(customFields.stream()
                    .collect(Collectors.toMap(EsqNameValue::getName, EsqNameValue::getValue)));
        }
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
                EsqEntity en = new EsqEntity();
                en.setId(node.getId());
                en.setName(node.getName());
                en.setKind(node.getKind());
                accounts.add(en);
            }
        }
    }

}

