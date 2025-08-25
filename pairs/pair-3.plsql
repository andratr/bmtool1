DECLARE
    v_exc_code          VARCHAR2(50);
    v_exc_category      VARCHAR2(50);
    v_sol_code          VARCHAR2(50);
    v_sys_identifier    VARCHAR2(100);
    v_report_date       DATE;
    v_exc_type          VARCHAR2(50);
    v_act_code          VARCHAR2(50);
    v_is_active         CHAR(1);
    v_default31days     NUMBER := 31;   -- guessing this is a constant
    v_pastduemax        NUMBER := 0;    -- guessing this is provided elsewhere
BEGIN
    -- Assign constants
    v_exc_code     := 'DAYPSTDRDF7';
    v_exc_category := 'EXP';
    v_sol_code     := 'DEFAULTED';

    -- First block: fetch exception info with error handler
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

    -- Conditional insert
    IF v_is_active = 'Y' THEN
        BEGIN
            dbms_output.put_line(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            'INFO,%1!,Create exceptions for code:%2!,exception category:%3!',
                            '%1!', utl_call_stack.dynamic_depth
                        ),
                        '%2!', v_exc_code
                    ),
                    '%3!', v_exc_category
                )
            );

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
            SELECT
                v_sys_identifier,
                v_report_date,
                v_exc_category,
                v_exc_type,
                v_exc_code,
                v_act_code,
                v_sol_code,
                ro.local_cust_id,  -- customer_id
                r.fac_id,          -- facility_id
                NULL,              -- cover_id
                ro.out_id,         -- outstanding_id
                pp.days_past_due,  -- local
                v_default31days,   -- crm
                'Days Past Due defaulted to ' || v_default31days, -- additional
                ro.raw_out_id      -- rowid
            FROM pp_out_days_past_due pp
            INNER JOIN raw_outstanding r ON r.raw_out_id = pp.raw_out_id
            INNER JOIN pp_out_past_due_amt ro ON ro.raw_out_id = pp.raw_out_id
            LEFT JOIN upload_exchange_rate e3 ON ro.past_due_amt_ccy = e3.currency_code -- STRY1251577
            WHERE pp.days_past_due = '0'
              AND NVL((ro.past_due_amt / e3.exchange_rate_per_euro), 0) > v_pastduemax -- STRY1251577
            ORDER BY raw_out_id;
        END;
    END IF;

    -- Merge block
    BEGIN
        MERGE INTO pp_out_days_past_due pp1
        USING (
            SELECT pp.ROWID row_id
            FROM pp_out_days_past_due pp
            JOIN pp_out_past_due_amt ro ON ro.raw_out_id = pp.raw_out_id
            LEFT JOIN upload_exchange_rate e3 ON ro.past_due_amt_ccy = e3.currency_code -- STRY1251577
            WHERE pp.days_past_due = '0'
              AND NVL((ro.past_due_amt / e3.exchange_rate_per_euro), 0) > v_pastduemax -- STRY1251577
        ) src
        ON (pp1.ROWID = src.row_id)
        WHEN MATCHED THEN
            UPDATE SET pp1.days_past_due = v_default31days;
    END;
END;
/
