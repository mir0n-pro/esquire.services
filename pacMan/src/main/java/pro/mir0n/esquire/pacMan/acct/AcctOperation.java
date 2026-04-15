/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/13/2026 mir0n  created: acct operation codes — AmountEffect (NEGATIVE/ANY/POSITIVE); Code enum (DEPOSIT/WITHDRAWAL/TRANSFER/ADJUSTMENT/COMMISSION/UNKNOWN) with id, effect, transfer flag, dict kind
 * 04/14/2026 mir0n  DICT_KIND_TRANSFER corrected 1002→1004; ACCT_KIND_PAPER = 54 added
 */

package pro.mir0n.esquire.pacMan.acct;


public class AcctOperation {
    public static final int    DICT_KIND_DEPOSIT = 1000;
    public static final int    DICT_KIND_WITHDRAWAL = 1002;
    public static final int    DICT_KIND_TRANSFER = 1004;
    public static final int    ACCT_KIND_PAPER = 54;


    public enum AmountEffect {
        NEGATIVE(-1),
        ANY(0), // any amount <> 0
        POSITIVE(1); // >0 only
        //ANY0(Integer.MIN_VALUE);// mimic NaN, any amount including 0

        public int id;
        private AmountEffect(int id) {
            this.id = id;
        }
    }

    public enum Code {
        UNKNOWN(0, AmountEffect.ANY, "Unknown", false, DICT_KIND_DEPOSIT),
        DEPOSIT(1, AmountEffect.POSITIVE, "Deposit", false, DICT_KIND_DEPOSIT),
        WITHDRAWAL(2, AmountEffect.NEGATIVE, "Withdrawal", false, DICT_KIND_WITHDRAWAL),
        TRANSFER(3, AmountEffect.NEGATIVE, "Transfer", true, DICT_KIND_TRANSFER), // Amount effect given for first leg
        ADJUSTMENT(4, AmountEffect.ANY, "Adj", false, DICT_KIND_DEPOSIT),
        COMMISSION(5, AmountEffect.NEGATIVE, "Comm", false, DICT_KIND_WITHDRAWAL);

        public int id;
        public AmountEffect effect;
        public String name;
        public boolean transfer;
        public int kind;
        private Code(int id, AmountEffect effect, String name, boolean transfer, int kind) {
            this.id = id;
            this.effect = effect;
            this.name = name;
            this.transfer = transfer;
            this.kind = kind;
        };

        public static Code valueOf(int id) {
            Code ret = UNKNOWN;
            for (Code c : Code.values()) {
                if (c.id == id) {
                    ret = c;
                    break;
                }
            }
            return ret;
        }
    }

}