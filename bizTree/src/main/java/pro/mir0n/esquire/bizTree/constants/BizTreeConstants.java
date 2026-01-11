/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n  added JWT_CLAIM_ENTITY_ID, JWT_CLAIM_ENTITY_ROOTPATH
 */

package pro.mir0n.esquire.bizTree.constants;

public final class BizTreeConstants {

    private BizTreeConstants() {
        // restrict instantiation
    }
    public static final String  STATUS_200 = "200";
    public static final String  MESSAGE_200 = "Request processed successfully";
    public static final String  STATUS_405 = "405";
    public static final String  MESSAGE_405 = "Wrong parameters";
    //public static final String  STATUS_417 = "417";
    //public static final String  MESSAGE_417_UPDATE= "Update operation failed. Please try again or contact Dev team";
    //public static final String  MESSAGE_417_DELETE= "Delete operation failed. Please try again or contact Dev team";
     public static final String  STATUS_500 = "500";
     public static final String  MESSAGE_500 = "An error occurred. Please try again or contact Dev team";

    public static final String  JWT_CLAIM_ENTITY_ID = "esq_uid";
    public static final String  JWT_CLAIM_ENTITY_ROOTPATH = "esq_rootpath";
}
