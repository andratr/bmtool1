DECLARE
    v_exc_code        VARCHAR2(50);
    v_exc_category    VARCHAR2(50);
    v_sol_code        VARCHAR2(50);
    v_sys_identifier  VARCHAR2(100);
    v_report_date     DATE;
    v_exc_type        VARCHAR2(50);
    v_act_code        VARCHAR2(50);
    v_is_active       CHAR(1);
    v_def_currency    VARCHAR2(10);
    v_description     VARCHAR2(500);
    v_proc_name       VARCHAR2(200) := 'EADREGCAPCCY_DEFAULTED';
    v_debug_msg       VARCHAR2(4000);
BEGIN
    -- Assign constants
    v_exc_code     := 'EADREGCAPCCY_DEFAULTED';
    v_exc_category := 'EXP';
    v_sol_code     := 'DEFAULTED';

    -- Get exception info
    BEGIN
        utilities.get_exception_info(
            v_exc_code,
            v_exc_category,
            v_sys_identifier,
            v_report_date,
            v_exc_type,
            v_act_code,
            v_sol_code,
            v_is_active
        );
    EXCEPTION
        WHEN OTHERS THEN
            utils.handleerror(SQLCODE, SQLERRM);
    END;

    -- If rule is active
    IF v_is_active = 'Y' THEN
        v_description := 'create exceptions for code: ' || v_exc_code ||
                         ', exception category: ' || v_exc_category;
        utilities.show_debug(v_proc_name || ' info => ' || v_description);

        -- Insert exceptions
        BEGIN
            INSERT /*+ APPEND enable_parallel_dml */
              INTO exceptions (sys_identifier,
                               report_date,
                               exc_category,
                               exc_type,
                               exc_code,
                               act_code,
                               sol_code,
                               cust_id,
                               cov_id,
                               fac_id,
                               loc_value,
                               crm_value,
                               add_info,
                               row_identifier)
            SELECT /*+ PARALLEL */ DISTINCT
                   v_sys_identifier,
                   v_report_date,
                   v_exc_category,
                   v_exc_type,
                   v_exc_code,
                   v_act_code,
                   v_sol_code,
                   rl.cust_id,                                        -- customer_id
                   NULL,                                              -- cover_id
                   rl.fac_id,                                         -- facility_id
                   rl.ead_regcap_ccy,                                 -- local value
                   v_def_currency,                                    -- crm value
                   'ead_regcap_ccy defaulted to ' || v_def_currency,  -- additional info
                   NULL                                               -- record_id
            FROM RAW_LIMIT rl
            WHERE rl.ead_regcap_ccy IS NULL
              AND rl.ead_regcap IS NOT NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit ||
                           ' rows affected: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;

        -- Default ead_regcap_ccy
        BEGIN
            UPDATE RAW_LIMIT
            SET ead_regcap_ccy = v_def_currency
            WHERE ead_regcap_ccy IS NULL
              AND ead_regcap IS NOT NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit ||
                           ' rows updated: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;
    END IF;
END;
/
