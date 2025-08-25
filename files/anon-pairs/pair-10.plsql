DECLARE
    v_msg                VARCHAR2(500);
    v_exc_code           VARCHAR2(50);
    v_exc_category       VARCHAR2(50);
    v_sol_code           VARCHAR2(50);
    v_sys_identifier     VARCHAR2(100);
    v_report_date        DATE;
    v_exc_type           VARCHAR2(50);
    v_act_code           VARCHAR2(50);
    v_is_active          CHAR(1);
    v_max_allowed_excess NUMBER;
    v_maxlevel           NUMBER;
    v_time_key           NUMBER;
BEGIN
    v_msg := 'DEBUG ' || current_timestamp || ' ret_exc_excess';
    utilities.show_debug(v_msg);

    -- Initialize
    v_is_active := 'N';
    v_exc_code     := 'LIMAMTCHK5'; -- STRY1079260
    v_exc_category := 'LIM';        -- STRY1079260
    v_sol_code     := 'IGNORED';
    v_time_key     := f_gettimekey(v_report_date);

    -- Fetch MAX_ALLOWED_EXCESS parameter
    BEGIN
        SELECT numeric_value
        INTO v_max_allowed_excess
        FROM functional_parameter
        WHERE code = 'MAX_ALLOWED_EXCESS'
          AND record_valid_until IS NULL;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_max_allowed_excess := NULL;
        WHEN OTHERS THEN
            RAISE;
    END;

    v_msg := '01 LIMAMTCHK5 exception';
    utilities.show_debug(v_msg);

    -- Get exception info
    BEGIN
        get_exception_info(
            v_exc_category   => v_exc_category,
            v_exc_code       => v_exc_code,
            iv_sol_code      => v_sol_code,
            v_sys_identifier => v_sys_identifier,
            v_act_code       => v_act_code,
            v_exc_type       => v_exc_type,
            v_is_active      => v_is_active
        );
    EXCEPTION
        WHEN OTHERS THEN
            utils.handleerror(SQLCODE, SQLERRM);
    END;

    v_msg := '02';
    utilities.show_debug(v_msg);

    IF v_is_active = 'Y' THEN
        -- Find maximum facility level
        SELECT MAX(child_level)
        INTO v_maxlevel
        FROM current_facility_type_tree;

        -- Truncate helper table
        utilities.truncate_table('tt_rosum');

        -- Populate tt_rosum with sum of remaining principal
        BEGIN
            INSERT INTO tt_rosum (concat_id, remain_princ_sum)
            SELECT ro.concat_id,
                   SUM(ro.remain_princ / er.exchange_rate) remain_princ_sum
            FROM raw_outstanding ro
            LEFT JOIN current_exchange_rate er
                   ON ro.orig_ccy_princ = er.currency_code
            WHERE ro.dummy_ind IS NULL
            GROUP BY ro.concat_id;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;
        COMMIT;

        -- Insert into exceptions table
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
                   rl.cust_id,
                   rl.high_level_fac_id,
                   NULL,
                   rl.fac_id,
                   (rl.limit_amt_ccy || ' ' || utils.convert_to_char(rl.limit_amt, 30)) loc_value,
                   ('EUR ' || CAST(CAST((rl.limit_amt / e1.exchange_rate) AS NUMBER(18, 2)) AS VARCHAR2(30))) crm_value,
                   'Limit Amount is less than sum of Outstanding Amounts.' add_info,
                   NULL
            FROM tt_rosum ro
            INNER JOIN raw_limit rl ON ro.concat_id = rl.concat_id
            LEFT JOIN exchange_rate e1
                   ON rl.orig_ccy_limit = e1.currency_code
                  AND e1.record_valid_from <= v_report_date
                  AND (e1.record_valid_until > v_report_date OR e1.record_valid_until IS NULL)
            WHERE ro.remain_princ_sum > (rl.limit_amt / e1.exchange_rate)
              AND rl.limit_amt <> 0
              AND rl.limit_amt IS NOT NULL
              AND ro.remain_princ_sum IS NOT NULL
              AND rl.credit_risk_rating NOT IN (
                    SELECT child_code
                    FROM current_risk_rating_tree
                    WHERE parent_code IN ('PROBLEMS'))
              AND rl.credit_risk_rating IS NOT NULL
              AND TRIM(rl.credit_risk_rating) <> ' '
              AND (ro.remain_princ_sum - (rl.limit_amt / e1.exchange_rate)) >= v_max_allowed_excess
              AND rl.dummy_ind IS NULL;

            utilities.show_debug('Inserted rows into exceptions: ' || SQL%ROWCOUNT);
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;
    END IF;

    COMMIT;
END;
/
