UPDATE skill_definition
SET skill_code = LEFT(skill_code, 64 - LENGTH('__deleted_' || CAST(id AS VARCHAR))) || '__deleted_' || CAST(id AS VARCHAR)
WHERE lifecycle_status = 'DELETED'
  AND skill_code NOT LIKE ('%__deleted_' || CAST(id AS VARCHAR));
