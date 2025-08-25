DECLARE
    v_exc_code        VARCHAR2(50);
    v_exc_category    VARCHAR2(50);
    v_sol_code        VARCHAR2(50);
    v_sys_identifier  VARCHAR2(100);
    v_report_date     DATE;
    v_exc_type        VARCHAR2(50);
    v_act_code        VARCHAR2(50);
    v_is_active       CHAR(1);
    v_proc_name       VARCHAR2(200) := 'LIM_FORMS1SDPOP_DEFAULTED';
    v_debug_msg       VARCHAR2(4000);
    v_description     VARCHAR2(500);
BEGIN
    -- Assign constants
    v_exc_code     := 'FORMS1SDPOP';
    v_exc_category := 'LIM';
    v_sol_code     := 'NIL';

    -- Get exception info
    BEGIN
        get_exception_info(
            v_sys_identifier => v_sys_identifier,
            v_exc_code       => v_exc_code,
            v_exc_category   => v_exc_category,
            iv_sol_code      => v_sol_code,
            v_exc_type       => v_exc_type,
            v_act_code       => v_act_code,
            v_is_active      => v_is_active
        );
    EXCEPTION
        WHEN OTHERS THEN
            utils.handleerror(SQLCODE, SQLERRM);
    END;

    -- Only if rule is active
    IF v_is_active = 'Y' THEN
        v_description := 'create exceptions for code: ' || v_exc_code ||
                         ', exception category: ' || v_exc_category;
        utilities.show_debug(v_proc_name || ' info => ' || v_description);

        -- Insert exceptions
        BEGIN
            INSERT INTO exceptions
                (sys_identifier,
                 report_date,
                 exc_category,
                 exc_type,
                 exc_code,
                 act_code,
                 sol_code,
                 cust_id,
                 high_level_fac_id,
                 cov_id,
                 fac_id,
                 loc_value,
                 crm_value,
                 add_info,
                 row_identifier)
            SELECT v_sys_identifier,
                   v_report_date,
                   v_exc_category,
                   v_exc_type,
                   v_exc_code,
                   v_act_code,
                   v_sol_code,
                   rl.cust_id,              -- customer_id
                   rl.high_level_fac_id,    -- higher_level_facility_id
                   NULL,                    -- cover_id
                   rl.fac_id,               -- facility_id
                   rl.forb_meas_start_date, -- local value
                   NULL,                    -- crm value
                   'forbearance measure start date set to null when forbearance measure is not available',
                   NULL                     -- record_id
            FROM raw_limit rl
            WHERE rl.forb_meas_start_date IS NOT NULL
              AND rl.forbearance_meas IS NULL
              AND rl.dummy_ind IS NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit ||
                           ' rows affected: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;

        -- Default forb_meas_start_date to NULL
        BEGIN
            UPDATE raw_limit rl
            SET rl.forb_meas_start_date = NULL
            WHERE rl.forb_meas_start_date IS NOT NULL
              AND rl.forbearance_meas IS NULL;

            v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || $$plsql_unit ||
                           ' rows updated: ' || SQL%ROWCOUNT;
            utilities.show_debug(v_debug_msg);

            COMMIT;
        EXCEPTION
            WHEN OTHERS THEN
                dbms_output.put_line('ERROR: Failed to default forb_meas_start_date for LIM_FORMS1SDPOP_DEFAULTED');
                utils.handleerror(SQLCODE, SQLERRM);
        END;
    END IF;
END;
/
