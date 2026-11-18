/* Check rate expiry for date of transaction in a given jurisdiction
This query is used to check if the rate for a given jurisdiction has expired for a given date of transaction. It will return the rate if it is valid for the given date, otherwise it will return no rows. */   
--Given variable for jurisdiction is ID_OF_JURISDICTION, date of transaction is DATE_OF_TRANSACTION

SELECT jurisdiction_id, effective_date, expiration_date, state_rate, local_rate, mctd_rate FROM sales_tax.rates
WHERE jurisdiction_id = ?
AND effective_date <= ?
AND (expiration_date IS NULL OR ? <= expiration_date)

