/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/13/2026 mir0n  created: account transaction result DTO; FIELD_* constants; fill(Map) sets desc/refCode fields
 * 04/20/2026 mir0n  FIELD_RATE added; response fields: ccy, convRate, amtIncoming, ccyIncoming; id type Long->String
 */

package pro.mir0n.esquire.pacMan.acct.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(name = "AcctTransactionSingle", description = "Account transaction result")
public class AcctTransactionSingle {

    public static final String FIELD_TYPE_ID  = "typeId";
    public static final String FIELD_AMOUNT   = "amount";
    public static final String FIELD_DESC     = "desc";
    public static final String FIELD_REF_CODE  = "refCode";
    public static final String FIELD_REF_CODE2 = "refCode2";
    public static final String FIELD_REF_CODE3 = "refCode3";
    public static final String FIELD_REF_CODE4 = "refCode4";
    public static final String FIELD_MEMO     = "memo";
    public static final String FIELD_ID2     = "id2";
    public static final String FIELD_KIND2   = "kind2";
    public static final String FIELD_RATE    = "rate";

    @Schema(description = "AccountID", example = "123456789")
    private String id;

    @Schema(description = "Account kind", example = "50")
    private int kind;

    @Schema(description = "Account currency code", example = "USD")
    private String ccy;

    @Schema(description = "Conversion rate applied (transfer only)")
    private Double convRate;

    @Schema(description = "Incoming amount before conversion (transfer credit leg only)")
    private Double amtIncoming;

    @Schema(description = "Incoming currency code (transfer credit leg only)", example = "EUR")
    private String ccyIncoming;

    @Schema(description = "Transaction type ID (from ESQ_ACTIVITY_TYPE)", example = "1")
    private Integer typeId;

    @Schema(description = "Transaction amount posted to account", example = "100.00")
    private Double amount;

    @Schema(description = "Transaction description")
    private String desc;

    @Schema(description = "Reference code, Payment method")
    private String refCode;

    @Schema(description = "Reference code, Reference code")
    private String refCode2;

    @Schema(description = "Reference code, Future use")
    private String refCode3;

    @Schema(description = "Reference code, Future use")
    private String refCode4;

    @Schema(description = "Memo")
    private String memo;

    public AcctTransactionSingle fill(Map<String, Object> fields) {
        Object rawDesc = fields.get(FIELD_DESC);
        if (rawDesc != null) desc = rawDesc.toString();
        Object rawRefCode = fields.get(FIELD_REF_CODE);
        if (rawRefCode != null) refCode = rawRefCode.toString();
        Object rawRefCode2 = fields.get(FIELD_REF_CODE2);
        if (rawRefCode2 != null) refCode2 = rawRefCode2.toString();
        Object rawRefCode3 = fields.get(FIELD_REF_CODE3);
        if (rawRefCode3 != null) refCode3 = rawRefCode3.toString();
        Object rawRefCode4 = fields.get(FIELD_REF_CODE4);
        if (rawRefCode4 != null) refCode4 = rawRefCode4.toString();
        Object rawMemo = fields.get(FIELD_MEMO);
        if (rawMemo != null) memo = rawMemo.toString();
        return this;
    }
}
