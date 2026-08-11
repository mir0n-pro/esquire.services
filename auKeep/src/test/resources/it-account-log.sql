-- Minimal schema for the (c) integration test: the account audit-log table + its dedup index.
-- Mirrors db.seed create.log (relaxed data cols + the dedup unique index).
CREATE TABLE IF NOT EXISTS esq_account_log (
  accl_action          VARCHAR(1)   DEFAULT 'I' NOT NULL,
  accl_pk              BIGINT       DEFAULT 0 NOT NULL,
  accl_et_pk           INT          DEFAULT 0 NOT NULL,
  accl_id              VARCHAR(50),
  accl_balance         NUMERIC(16,3) DEFAULT 0,
  accl_ccy             VARCHAR(3)   DEFAULT 'USD',
  accl_status          VARCHAR(1)   DEFAULT 'O',
  accl_usr_pk          BIGINT       DEFAULT 0,
  accl_desc            VARCHAR(1024),
  accl_funded_dt       DATE,
  accl_neg_allowed_flg VARCHAR(1)   DEFAULT 'N',
  accl_change_no       BIGINT       NOT NULL,
  accl_crl_id          VARCHAR(64),
  accl_req_id          VARCHAR(64),
  accl_uid             VARCHAR(16),
  accl_action_ts       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX esq_account_log_dedup_uk ON esq_account_log (accl_pk, accl_change_no);
