/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created (was enyMan.rod.RodLogSql): vendor-keyed *_log INSERT/MERGE for the AUDIT use
 *                   of x-Rod. Lives under common.audit (NOT common.xrod) -- x-Rod is a generic fan-out
 *                   substrate; this is one sink (audit). Identity params are uniform (:entityId/:kind/:subId
 *                   from the RodEvent header); data params are the entity property names (from IMappable.
 *                   fillMap), so the body binds straight through. Postgres: INSERT .. ON CONFLICT DO NOTHING;
 *                   Oracle: MERGE .. WHEN NOT MATCHED. Shared by enyMan / pacMan / keySmith.
 */
package pro.mir0n.esquire.common.audit;

import java.util.Map;

public final class AuditLogSql {

    // Logical statement keys (one per *_log table).
    public static final String ORG     = "org";
    public static final String ORG_PAR = "orgPar";
    public static final String USER    = "user";
    public static final String PERSON  = "person";
    public static final String ADDRESS = "address";
    public static final String USR_PAR = "usrPar";
    public static final String ACCOUNT = "account";
    public static final String AUTH    = "auth";

    private static final Map<String, String> POSTGRES = Map.ofEntries(
        Map.entry(ORG, """
             INSERT INTO esq_org_log
               (orgl_action, orgl_pk, orgl_et_pk, orgl_name, orgl_full_name, orgl_org_pk, orgl_desc,
                orgl_crl_id, orgl_req_id, orgl_uid, orgl_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :kind, :name, :fullName, CAST(:parentId AS bigint), :desc,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(ORG_PAR, """
             INSERT INTO esq_org_par_log
               (oprl_action, oprl_org_pk, oprl_par_name, oprl_par_et_pk, oprl_value,
                oprl_crl_id, oprl_req_id, oprl_uid, oprl_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :subId, :etPk, :value,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(USER, """
             INSERT INTO esq_user_log
               (usrl_action, usrl_pk, usrl_et_pk, usrl_name, usrl_reg_option, usrl_org_pk, usrl_deleted_flg,
                usrl_desc, usrl_crl_id, usrl_req_id, usrl_uid, usrl_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :kind, :name, :registration, CAST(:parentId AS bigint), :deleted,
                :desc, :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(PERSON, """
             INSERT INTO esq_person_log
               (pel_action, pel_usr_pk, pel_kind, pel_first_name, pel_middle_name, pel_last_name, pel_title,
                pel_dob, pel_birth_place, pel_sex, pel_tax_id, pel_citizenship, pel_mar_status,
                pel_person_id_type, pel_person_id_number, pel_email, pel_phone, pel_phone2,
                pel_crl_id, pel_req_id, pel_uid, pel_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :kind, :firstName, :middleName, :lastName, :title,
                CAST(NULLIF(:dob,'') AS date), :birthPlace, :sex, :taxId, :citizenship, :marStatus,
                :personIdType, :personIdNumber, :email, :phone, :phone2,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(ADDRESS, """
             INSERT INTO esq_address_log
               (adl_action, adl_pk, adl_addr, adl_addr2, adl_city, adl_company, adl_country, adl_department,
                adl_desc, adl_fax, adl_postal_code, adl_province, adl_title, adl_url,
                adl_crl_id, adl_req_id, adl_uid, adl_action_ts)
             VALUES
               (:action, CAST(:subId AS bigint), :addr, :addr2, :city, :company, :country, :department,
                :desc, :fax, :postalCode, :province, :title, :url,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(USR_PAR, """
             INSERT INTO esq_usr_par_log
               (uprl_action, uprl_usr_pk, uprl_par_name, uprl_par_et_pk, uprl_value,
                uprl_crl_id, uprl_req_id, uprl_uid, uprl_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :subId, :etPk, :value,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(ACCOUNT, """
             INSERT INTO esq_account_log
               (accl_action, accl_pk, accl_et_pk, accl_id, accl_balance, accl_ccy, accl_status, accl_usr_pk,
                accl_desc, accl_funded_dt, accl_neg_allowed_flg, accl_crl_id, accl_req_id, accl_uid, accl_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :kind, :name, :balance, :ccy, :status, CAST(:parentId AS bigint),
                :desc, CAST(NULLIF(:fundedDate,'') AS date), :negativeAllowed, :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """),
        Map.entry(AUTH, """
             INSERT INTO esq_auth_log
               (aul_action, aul_usr_pk, aul_connect_flg, aul_login_id, aul_email, aul_tfa_method,
                aul_force_change_flg, aul_security_question, aul_security_answer,
                aul_crl_id, aul_req_id, aul_uid, aul_action_ts)
             VALUES
               (:action, CAST(:entityId AS bigint), :connectFlg, :loginId, :email, :tfaMethod,
                :forceChangeFlg, :securityQuestion, :securityAnswer,
                :crl, :req, :uid, :actionTs)
             ON CONFLICT DO NOTHING
             """)
    );

    private static final Map<String, String> ORACLE = Map.ofEntries(
        Map.entry(ORG, """
             MERGE INTO esq_org_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS pk FROM dual) s
             ON (d.orgl_crl_id = s.crl AND d.orgl_pk = s.pk)
             WHEN NOT MATCHED THEN INSERT
               (orgl_action, orgl_pk, orgl_et_pk, orgl_name, orgl_full_name, orgl_org_pk, orgl_desc,
                orgl_crl_id, orgl_req_id, orgl_uid, orgl_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :kind, :name, :fullName, TO_NUMBER(:parentId), :desc,
                :crl, :req, :uid, :actionTs)
             """),
        Map.entry(ORG_PAR, """
             MERGE INTO esq_org_par_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS org_pk, :subId AS par_name FROM dual) s
             ON (d.oprl_crl_id = s.crl AND d.oprl_org_pk = s.org_pk AND d.oprl_par_name = s.par_name)
             WHEN NOT MATCHED THEN INSERT
               (oprl_action, oprl_org_pk, oprl_par_name, oprl_par_et_pk, oprl_value,
                oprl_crl_id, oprl_req_id, oprl_uid, oprl_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :subId, :etPk, :value,
                :crl, :req, :uid, :actionTs)
             """),
        Map.entry(USER, """
             MERGE INTO esq_user_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS pk FROM dual) s
             ON (d.usrl_crl_id = s.crl AND d.usrl_pk = s.pk)
             WHEN NOT MATCHED THEN INSERT
               (usrl_action, usrl_pk, usrl_et_pk, usrl_name, usrl_reg_option, usrl_org_pk, usrl_deleted_flg,
                usrl_desc, usrl_crl_id, usrl_req_id, usrl_uid, usrl_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :kind, :name, :registration, TO_NUMBER(:parentId), :deleted,
                :desc, :crl, :req, :uid, :actionTs)
             """),
        Map.entry(PERSON, """
             MERGE INTO esq_person_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS usr_pk, :kind AS kind FROM dual) s
             ON (d.pel_crl_id = s.crl AND d.pel_usr_pk = s.usr_pk AND d.pel_kind = s.kind)
             WHEN NOT MATCHED THEN INSERT
               (pel_action, pel_usr_pk, pel_kind, pel_first_name, pel_middle_name, pel_last_name, pel_title,
                pel_dob, pel_birth_place, pel_sex, pel_tax_id, pel_citizenship, pel_mar_status,
                pel_person_id_type, pel_person_id_number, pel_email, pel_phone, pel_phone2,
                pel_crl_id, pel_req_id, pel_uid, pel_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :kind, :firstName, :middleName, :lastName, :title,
                TO_DATE(NULLIF(:dob,''),'YYYY-MM-DD'), :birthPlace, :sex, :taxId, :citizenship, :marStatus,
                :personIdType, :personIdNumber, :email, :phone, :phone2,
                :crl, :req, :uid, :actionTs)
             """),
        Map.entry(ADDRESS, """
             MERGE INTO esq_address_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:subId) AS pk FROM dual) s
             ON (d.adl_crl_id = s.crl AND d.adl_pk = s.pk)
             WHEN NOT MATCHED THEN INSERT
               (adl_action, adl_pk, adl_addr, adl_addr2, adl_city, adl_company, adl_country, adl_department,
                adl_desc, adl_fax, adl_postal_code, adl_province, adl_title, adl_url,
                adl_crl_id, adl_req_id, adl_uid, adl_action_ts)
             VALUES
               (:action, TO_NUMBER(:subId), :addr, :addr2, :city, :company, :country, :department,
                :desc, :fax, :postalCode, :province, :title, :url,
                :crl, :req, :uid, :actionTs)
             """),
        Map.entry(USR_PAR, """
             MERGE INTO esq_usr_par_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS usr_pk, :subId AS par_name FROM dual) s
             ON (d.uprl_crl_id = s.crl AND d.uprl_usr_pk = s.usr_pk AND d.uprl_par_name = s.par_name)
             WHEN NOT MATCHED THEN INSERT
               (uprl_action, uprl_usr_pk, uprl_par_name, uprl_par_et_pk, uprl_value,
                uprl_crl_id, uprl_req_id, uprl_uid, uprl_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :subId, :etPk, :value,
                :crl, :req, :uid, :actionTs)
             """),
        Map.entry(ACCOUNT, """
             MERGE INTO esq_account_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS pk FROM dual) s
             ON (d.accl_crl_id = s.crl AND d.accl_pk = s.pk)
             WHEN NOT MATCHED THEN INSERT
               (accl_action, accl_pk, accl_et_pk, accl_id, accl_balance, accl_ccy, accl_status, accl_usr_pk,
                accl_desc, accl_funded_dt, accl_neg_allowed_flg, accl_crl_id, accl_req_id, accl_uid, accl_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :kind, :name, :balance, :ccy, :status, TO_NUMBER(:parentId),
                :desc, TO_DATE(NULLIF(:fundedDate,''),'YYYY-MM-DD'), :negativeAllowed, :crl, :req, :uid, :actionTs)
             """),
        Map.entry(AUTH, """
             MERGE INTO esq_auth_log d
             USING (SELECT :crl AS crl, TO_NUMBER(:entityId) AS usr_pk FROM dual) s
             ON (d.aul_crl_id = s.crl AND d.aul_usr_pk = s.usr_pk)
             WHEN NOT MATCHED THEN INSERT
               (aul_action, aul_usr_pk, aul_connect_flg, aul_login_id, aul_email, aul_tfa_method,
                aul_force_change_flg, aul_security_question, aul_security_answer,
                aul_crl_id, aul_req_id, aul_uid, aul_action_ts)
             VALUES
               (:action, TO_NUMBER(:entityId), :connectFlg, :loginId, :email, :tfaMethod,
                :forceChangeFlg, :securityQuestion, :securityAnswer,
                :crl, :req, :uid, :actionTs)
             """)
    );

    private AuditLogSql() {
    }

    /** SQL for the given statement key in the given vendor's dialect (true = Oracle, false = Postgres). */
    public static String forVendor(boolean oracle, String key) {
        String ret = (oracle ? ORACLE : POSTGRES).get(key);
        if (ret == null) {
            throw new IllegalArgumentException("no audit-log SQL for key: " + key);
        }
        return ret;
    }
}
