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
 * 04/09/2026 mir0n  fundedDate, negativeAllowed fields added; wired in fillDetails()
 */


package pro.mir0n.esquire.backend.dto.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

import java.util.List;

@Data
@Schema(
        name = "EsqAcct",
        description = "Holds Account entity state. Extends EsqEntity"
)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class EsqAcct extends EsqEntity {
    public EsqAcct() {
        super();
    }

    @Schema(
            description = "Account denomination", example = "USD"
    )
    private String ccy;

    @Schema(
            description = "Current balance", example = "1000"
    )
    private Double balance;

    @Schema(
            description = "Account Status", example = "O"
    )
    private String status;

    @Schema(
            description = "Date when the account was funded, ISO-8601: YYYY-MM-DD", example = "2026/04/09"
    )
    private String fundedDate;

    @Schema(
            description = "Negative balance allowed flag", example = "N"
    )
    private String negativeAllowed;

    @Override
    protected  void fillDetails(EsqEntityJpa jpa) {
       this.ccy = ((EsqAcctJpa)jpa).getCcy();
       this.balance = ((EsqAcctJpa)jpa).getBalance();
       this.status = ((EsqAcctJpa)jpa).getStatus();
       this.fundedDate = ((EsqAcctJpa)jpa).getFundedDate();
       this.negativeAllowed = ((EsqAcctJpa)jpa).getNegativeAllowed();
    }
    @Override
    protected void fillCustom(List<EsqNameValueJpa> custom) {}

    @Override
    protected void fillChildren(List<EsqEntityJpa> children) {}

    @Override
    protected void fillPerson(EsqEntityJpa person) {}
    @Override
    protected void fillAddress(EsqEntityJpa address) {}
    @Override
    protected void fillBizAddress(EsqEntityJpa address) {}

}

