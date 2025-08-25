DECLARE
    v_exc_code        VARCHAR2(50);
    v_exc_category    VARCHAR2(50);
    v_sol_code        VARCHAR2(50);
    v_sys_identifier  VARCHAR2(100);
    v_report_date     DATE;
    v_exc_type        VARCHAR2(50);
    v_act_code        VARCHAR2(50);
    v_is_active       CHAR(1);
BEGIN
    -- Assign constants
    v_exc_code     := 'PSTDUAMTCHK2';
    v_exc_category := 'EXP';
    v_sol_code     := 'IGNORED';

    -- Fetch exception info with error handling
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

    dbms_output.put_line('v_is_active>>' || v_is_active);

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
                r.local_cust_id, -- customer_id
                r.fac_id,        -- facility_id
                NULL,            -- cover_id
                r.out_id,        -- outstanding_id
                'EUR' || CAST(CAST((pp.past_due_amt / e3.exchange_rate_per_euro) AS NUMBER(18, 2)) AS VARCHAR2(22)), -- local
                'EUR' || CAST(CAST((pp.past_due_amt / e3.exchange_rate_per_euro) AS NUMBER(18, 2)) AS VARCHAR2(22)), -- crm
                'Past Due Amount is larger than (Principal, Interest, and Fees Past Due Amount).', -- additional info
                r.raw_out_id -- rowid
            FROM raw_outstanding r
            JOIN pp_out_past_due_amt pp
              ON pp.raw_out_id = r.raw_out_id
            LEFT JOIN upload_exchange_rate e
              ON r.principal_past_due_amt_ccy = e.currency_code
            LEFT JOIN upload_exchange_rate e1
              ON r.interest_past_due_amt_ccy = e1.currency_code
            LEFT JOIN upload_exchange_rate e2
              ON r.fees_past_due_amt_ccy = e2.currency_code
            LEFT JOIN upload_exchange_rate e3
              ON pp.past_due_amt_ccy = e3.currency_code
            WHERE CAST(NVL((pp.past_due_amt / e3.exchange_rate_per_euro), 0) AS NUMBER(22, 2))
                  > CAST( ( NVL((r.principal_past_due_amt / e.exchange_rate_per_euro), 0)
                          + NVL((r.interest_past_due_amt / e1.exchange_rate_per_euro), 0)
                          + NVL((r.fees_past_due_amt / e2.exchange_rate_per_euro), 0) )
                        AS NUMBER(22, 2))
              AND CAST( ( NVL((r.principal_past_due_amt / e.exchange_rate_per_euro), 0)
                        + NVL((r.interest_past_due_amt / e1.exchange_rate_per_euro), 0)
                        + NVL((r.fees_past_due_amt / e2.exchange_rate_per_euro), 0) )
                      AS NUMBER(22, 2)) != 0;
        EXCEPTION
            WHEN OTHERS THEN
                utils.handleerror(SQLCODE, SQLERRM);
        END;
    END IF;
END;
/
