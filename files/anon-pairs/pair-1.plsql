DECLARE
    v_exc_code        VARCHAR2(50);
    v_exc_category    VARCHAR2(50);
    v_sol_code        VARCHAR2(50);
    v_sys_identifier  VARCHAR2(100);
    v_report_date     DATE;
    v_exc_type        VARCHAR2(50);
    v_act_code        VARCHAR2(50);
    v_is_active       CHAR(1);
    v_max_allowed_excess NUMBER := 0;  -- assuming you want this defined
BEGIN
    -- ============================================
    -- 1. Assign constants
    -- ============================================
    v_exc_code     := 'DAYPSTDCHK1';
    v_exc_category := 'EXP';
    v_sol_code     := 'IGNORED';

    -- ============================================
    -- 2. Fetch exception info with error handling
    -- ============================================
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

    -- ============================================
    -- 3. Conditional insert into EXCEPTION table
    -- ============================================
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
                vo.cust_id,         -- customer_id
                vo.fac_id,          -- facility_id
                NULL,               -- cover_id
                vo.local_out_id,    -- outstanding_id
                vo.days_past_due,   -- local
                vc.bnk_risk_rating, -- crm
                NULL,               -- additional
                NULL                -- rowid
            FROM
                valid_outstanding vo
            JOIN valid_outstanding_amount voa ON vo.out_id = voa.out_id
            JOIN valid_customer vc ON vo.cust_id = vc.cust_id
            JOIN upload_exchange_rate e ON vo.orig_currency = e.currency_code
            WHERE utils.convert_to_number(vo.days_past_due, 18) > 90
              AND (voa.outstanding_amt / e.exchange_rate_per_euro) >= v_max_allowed_excess
              AND vc.bnk_risk_rating NOT IN (
                    SELECT child_code
                    FROM current_risk_rating_tree
                    WHERE parent_code IN (
                        SELECT reference_value
                        FROM functional_parameter
                        WHERE code = 'RISK_RATING_PROBLEM'
                          AND record_valid_until IS NULL
                    )
              );
        END;
    END IF;
END;
/
