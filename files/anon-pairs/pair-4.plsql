DECLARE
    v_exc_category   VARCHAR2(50);
    v_sol_code       VARCHAR2(50);
    v_exc_code       VARCHAR2(50);
    v_sys_identifier VARCHAR2(100);
    v_report_date    DATE;
    v_exc_type       VARCHAR2(50);
    v_act_code       VARCHAR2(50);
    v_is_active      CHAR(1);
    v_debug_msg      VARCHAR2(4000);
    v_proc_name      VARCHAR2(100) := 'ENDDATERDF3H'; -- just an example
BEGIN
    -- Assign constants
    v_exc_category := 'EXP';
    v_sol_code     := 'DEFAULTED';
    v_exc_code     := 'ENDDATERDF3H';

    -- Call exception info with handler
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

    -- Conditional insert
    IF v_is_active = 'Y' THEN
        INSERT /*+ APPEND enable_parallel_dml */ INTO exceptions (
            sys_identifier,
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
            row_identifier
        )
        SELECT
            v_sys_identifier,
            v_report_date,
            v_exc_category,
            v_exc_type,
            v_exc_code,
            v_act_code,
            v_sol_code,
            o.cust_id,             -- customer_id
            o.high_level_fac_id,   -- higher_level_facility_id
            NULL,                  -- cover_id
            o.fac_id,              -- facility_id
            o.out_end_date,        -- local value
            offset.offset_date,    -- crm value (STRY0247022)
            'Outstanding end date defaulted to ' || offset.offset_date, -- comment
            NULL                   -- record_id
        FROM raw_outstanding o
        JOIN tt_ro_offset offset
          ON o.fac_id = offset.fac_id
         AND o.high_level_fac_id = offset.high_level_fac_id
         AND o.cust_id = offset.cust_id
        JOIN tt_dd_maturity_data_fac mdc
          ON offset.fac_type = mdc.child_code
         AND mdc.defaulting_type = 'maturity offset'
         AND offset.pastdue_date <= v_report_date
         AND offset.limit_end_date >= offset.offset_date
         AND o.dummy_ind IS NULL
        LEFT JOIN current_loan_status ls
          ON offset.status_code = ls.code -- STRY0443174
        WHERE NVL(ls.derecognised_ind, 'N') = 'N'; -- STRY0443174

        v_debug_msg := $$plsql_line || ' Of PLSQL UNIT ' || v_proc_name ||
                       ' exceptions inserted for ENDDATERDF3H rowcount ' || SQL%ROWCOUNT;
        utilities.show_debug(v_debug_msg);
        COMMIT;
    END IF;

    -- Merge block
    BEGIN
        MERGE INTO raw_outstanding r
        USING (
            SELECT MAX(offset.offset_date) offset_date,
                   offset.fac_id,
                   offset.high_level_fac_id,
                   offset.cust_id
            FROM tt_ro_offset offset
            JOIN tt_dd_maturity_data_fac mdc
              ON offset.fac_type = mdc.child_code
             AND mdc.defaulting_type = 'maturity offset'
            JOIN current_loan_status ls
              ON offset.status_code = ls.code -- STRY0443174
            WHERE offset.pastdue_date <= v_report_date
              AND offset.limit_end_date >= offset.offset_date
              AND NVL(ls.derecognised_ind, 'N') = 'N' -- STRY0443174
            GROUP BY offset.fac_id, offset.high_level_fac_id, offset.cust_id
        ) src
        ON (r.fac_id = src.fac_id
            AND r.high_level_fac_id = src.high_level_fac_id
            AND r.cust_id = src.cust_id)
        WHEN MATCHED THEN
            UPDATE SET out_end_date = src.offset_date; -- STRY0247022
    EXCEPTION
        WHEN OTHERS THEN
            RAISE;
    END;
END;
/
