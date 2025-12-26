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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.jpa.EsqEntityJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.bizTree.jpa.entity.EsqAcctJpa;

import java.util.List;

@Data //xxx:important
@Schema(
        name = "Account",
        description = "Schema to holdGeneric Entity information"
)

public class EsqAcct extends EsqEntity {
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

    @Override
    protected  void fillDetails(EsqEntityJpa jpa) {
       this.ccy = ((EsqAcctJpa)jpa).getCcy();
       this.balance = ((EsqAcctJpa)jpa).getBalance();
       this.status= ((EsqAcctJpa)jpa).getStatus();
    }
    @Override
    protected void fillCustom(List<EsqNameValueJpa> custom) {}

    @Override
    protected void fillChildren(List<EsqTreeNodeJpa> children) {}


}

