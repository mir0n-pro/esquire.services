/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/27/2025  mir0n extend EsqEntity correctly
 */

package pro.mir0n.esquire.backend.dto.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqNameValue;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Schema(
        name = "EsqOrg",
        description = "Holds Organization entity state, possible custom fields. Extends EsqEntity"
)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class EsqOrg extends EsqEntity {
    public EsqOrg() {
        super();
    }

   @Schema(
            description = "Organization full name", example = "xxx"
    )
    private String fullName;

//    @JsonIgnore
    @Schema(
            description = "Custom fields, The list is  converting into fields for serialization.",
            example = "DB_VERSION: '2.0.0', DB_NAME: 'Esquire'"
    )
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

