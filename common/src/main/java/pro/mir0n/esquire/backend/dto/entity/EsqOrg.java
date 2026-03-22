/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/27/2025  mir0n extend EsqEntity correctly
 * 02/13/2026 mir0n  use EsqEntityJpa for children
 * 02/28/2026 mir0n  empty fillPerson/fillAddress/fillBizAddress stubs added
 * 03/06/2026 mir0n  customFields: null-safe LinkedHashMap instead of Collectors.toMap()
 */

package pro.mir0n.esquire.backend.dto.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqNameValue;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, String> ret = new LinkedHashMap<>();
        if (customFields != null) {
            for (EsqNameValue nv : customFields) {
                ret.put(nv.getName(), nv.getValue());
            }
        }
        return ret;
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
    protected void fillChildren(List<EsqEntityJpa> children) {}
    @Override
    protected void fillPerson(EsqEntityJpa person) {}
    @Override
    protected void fillAddress(EsqEntityJpa address) {}
    @Override
    protected void fillBizAddress(EsqEntityJpa address) {}

}

