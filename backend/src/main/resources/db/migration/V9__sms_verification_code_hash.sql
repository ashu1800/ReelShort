alter table sms_verification_codes
    add column if not exists code_hash varchar(128);

update sms_verification_codes
   set code_hash = 'legacy-invalid-' || id
 where code_hash is null;

alter table sms_verification_codes
    alter column code_hash set not null;
