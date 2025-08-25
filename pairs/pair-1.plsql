v_exc_code := 'DAYPSTDCHK1';
v_exc_category := 'EXP';
v_sol_code := 'IGNORED';

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
        utils.handleerror(
            sqlcode,
            sqlerrm
        );
END;

BEGIN
    IF v_is_active = 'Y' THEN
        BEGIN
            dbms_output.put_line(replace(
                replace(
                    replace(
                        'INFO,%1!,Create exceptions for code:%2!,exception category:%3!',
                        '%1!',
                        utl_call_stack.dynamic_depth
                    ),
                    '%2!',
                    v_exc_code
                ),
                '%3!',
                v_exc_category
            ));

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
                vo.cust_id,          --customer_id
                vo.fac_id,           --facility_id
                NULL,                --cover_id
                vo.local_out_id,     --outstanding_id
                vo.days_past_due,    --local
                vc.bnk_risk_rating,  --crm
                NULL,                --additional
                NULL                 --rowid
            FROM
                valid_outstanding vo,
                valid_outstanding_amount voa,
                valid_customer vc,
                upload_exchange_rate e  --ORAMIG catchup5
            WHERE
                vo.out_id = voa.out_id
                AND vo.cust_id = vc.cust_id
                AND vo.orig_currency = e.currency_code
                AND utils.convert_to_number(vo.days_past_due, 18) > 90  -- The outstanding is more than 90 days overdue
                AND (voa.outstanding_amt / e.exchange_rate_per_euro) >= v_max_allowed_excess  -- The outstanding amount, corrected for currency, is greater than the maximum allowed excess outstanding
                AND vc.bnk_risk_rating NOT IN (
                    SELECT
                        child_code
                    FROM
                        current_risk_rating_tree
                    WHERE
                        parent_code IN (
                            SELECT
                                reference_value
                            FROM
                                functional_parameter
                            WHERE
                                code = 'RISK_RATING_PROBLEM'
                                AND record_valid_until IS NULL
                        )  -- The loan is not a "problem" loan, so we don't expect such a large amount overdue for so long
                );
        END;
    END IF;
END;