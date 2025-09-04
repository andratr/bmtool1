DECLARE
    v_exc_code        VARCHAR2(50);
    v_exc_category    VARCHAR2(50);
    v_sol_code        VARCHAR2(50);
    v_sys_identifier  VARCHAR2(100);
    v_report_date     DATE;
    v_exc_type        VARCHAR2(50);
    v_act_code        VARCHAR2(50);
    v_is_active       CHAR(1);

    -- constants / tuning parameters
    v_default_days    NUMBER := 31;   -- default days past due
    v_pastduemax      NUMBER := 0;    -- threshold, provided externally
BEGIN
    -- ============================================
    -- 1. Assign constants
    -- ============================================
    v_exc_code     := 'DAYPSTDRDF3C';
    v_exc_category := 'EXP';
    v_sol_code     := 'DEFAULTED';

    -- ============================================
    -- 2. Fetch exception info with error handling
    -- ============================================
    BEGIN
        get_exception_info(
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

    -- ============================================
    -- 3. Conditional insert into EXCEPTIONS table
    -- ============================================
    IF v_is_active = 'Y' THEN
        BEGIN
            INSERT /*+ APPEND enable_parallel_dml */
            INTO exceptions (
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
                rb.cust_id, -- customer_id
                rb.high_level_fac_id, -- higher_level_facility_id
                NULL, -- cover_id
                rb.fac_id, -- facility_id
                NULL, -- local value
                utils.round_(periods_overdue * tmp.num_of_days
                            - (next_payment_date - v_report_date), 0), -- crm value
                'Days pastdue is defaulted to ' ||
                  utils.round_(periods_overdue * tmp.num_of_days
                            - (next_payment_date - v_report_date), 0), -- description
                NULL -- row identifier
            FROM related_borrower rb
            JOIN tt_period_4 tmp ON rb.related_borrower_key = tmp.related_borrower_key
            WHERE rb.periods_overdue > 0
              AND rb.next_payment_date IS NOT NULL
              AND rb.next_payment_date <= v_report_date + tmp.num_of_days
              AND rb.sys_identifier = v_sys_identifier
              AND rb.time_key = v_time_key
              AND rb.days_pastdue IS NULL
              AND rb.term_payment IS NULL
              AND rb.dummy_ind IS NULL;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE;
        END;
        COMMIT;
    END IF;

    -- ============================================
    -- 4. Merge update on related_borrower
    -- ============================================
    BEGIN
        MERGE INTO related_borrower r
        USING (
            SELECT num_of_days, related_borrower_key
            FROM tt_period_4
        ) tmp
        ON (
            r.related_borrower_key = tmp.related_borrower_key
            AND r.periods_overdue > 0
            AND r.next_payment_date IS NOT NULL
            AND r.next_payment_date <= v_report_date + tmp.num_of_days
            AND r.sys_identifier = v_sys_identifier
            AND r.time_key = v_time_key
            AND r.term_payment IS NULL
        )
        WHEN MATCHED THEN
            UPDATE SET r.days_pastdue =
                          ROUND(r.periods_overdue * tmp.num_of_days
                             - (r.next_payment_date - v_report_date), 0),
                       r.days_pastdue_value =
                          ROUND(r.periods_overdue * tmp.num_of_days
                             - (r.next_payment_date - v_report_date), 0),
                       r.days_pastdue_unit = 'D'
        WHERE r.days_pastdue IS NULL;
    EXCEPTION
        WHEN OTHERS THEN
            RAISE;
    END;
END;
/
