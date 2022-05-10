-- Ensure migration works on older copies of the database
UPDATE certificateauthority
   SET manifest_and_crl_check_needed = TRUE
 WHERE manifest_and_crl_check_needed IS NULL
   AND type <> 'NONHOSTED';

ALTER TABLE certificateauthority
  ADD CONSTRAINT manifest_and_crl_check_needed CHECK (CASE type
                                                      WHEN 'NONHOSTED' THEN manifest_and_crl_check_needed IS NULL
                                                      ELSE manifest_and_crl_check_needed IS NOT NULL
                                                      END);
