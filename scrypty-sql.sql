select * from transactions t 
where t.created_date is null
;



select count(*) as ile from transactions t 
where t.created_date is null
;

update transactions t
set created_date = '2026-05-09 21:43:02.750'
where t.created_date is null
;