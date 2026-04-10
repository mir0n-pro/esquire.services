/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/09/2026 mir0n  created: account transaction command result DTO
 */

package pro.mir0n.esquire.pacMan.acct.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "AcctTransactionSimple", description = "Account transaction result")
public class AcctTransactionSimple {

    @Schema(description = "Transaction ID", example = "123456789")
    private String id;

    @Schema(description = "Transaction entity kind (980 = accttr)", example = "980")
    private int kind;

    @Schema(description = "Transaction type ID (from ESQ_ACTIVITY_TYPE)", example = "1")
    private Integer typeId;

    @Schema(description = "Transaction amount posted to account", example = "100.00")
    private Double amount;

    @Schema(description = "Transaction description")
    private String desc;

    @Schema(description = "Payment method")
    private String refCode;

    @Schema(description = "Reference code")
    private String refCode2;

    @Schema(description = "Future use")
    private String refCode3;

    @Schema(description = "Future use")
    private String refCode4;

    @Schema(description = "Memo")
    private String memo;
}
