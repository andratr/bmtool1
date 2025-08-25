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
    v_debug_msg       VARCHAR2(4000);
BEGIN
    -- Assign constants
    v_exc_code     := 'EADIFR9CCY_DEFAULTED';
    v_exc_category := 'EXP';
    v_sol_code     := 'DEFAULTED';

    -- Fetch exception info
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

    IF v_is_active = 'Y' THEN
        BEGIN
            INSERT INTO exception (
                sys_identifier,
                report_date,
                exc_category,
                exc_type,
                exc_code,
                act_code,
                sol_code,
                local_cust_id,
                fac_id,
                cov_id,
                out_id,
                loc_value,
                crm_value,
                add_info,
                row_identifier
            )
            SELECT DISTINCT
                v_sys_identifier,
                v_report_date,
                v_exc_category,
                v_exc_type,
                v_exc_code,
                v_act_code,
                v_sol_code,
                rf.local_cust_id,   -- customer_id
                rf.fac_id,          -- facility_id
                NULL,               -- cover_id
                NULL,               -- outstanding_id
                rf.ead_ifr9_ccy,    -- local
                v_def_currency,     -- crm
                'ead_ifr9_ccy defaulted to ' || v_def_currency, -- additional
                rf.raw_fac_id       -- rowid
            FROM pp_fac_ead rf
            WHERE rf.ead_ifr9_ccy IS NULL
              AND rf.ead_ifr9 IS NOT NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit
                            || ' rows affected: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;

        -- Defaulting the ead_ifr9_ccy
        BEGIN
            UPDATE pp_fac_ead
            SET ead_ifr9_ccy = v_def_currency
            WHERE ead_ifr9_ccy IS NULL
              AND ead_ifr9 IS NOT NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit
                            || ' rows updated: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;
    END IF;
END;
/
