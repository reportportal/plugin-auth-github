DO
'
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ''integration_backup'') THEN

        -- restore saml integration from backup
        INSERT INTO integration (name, type, params, creator, creation_date, enabled)
        SELECT ib.name,
               (SELECT it.id FROM integration_type it WHERE it.auth_flow = ''OAUTH'' AND it.group_type = ''AUTH'' AND it.plugin_type = ''EXTENSION''),
               ib.params,
               ''SYSTEM'',
               now(),
               true
        FROM integration_backup ib
        WHERE ib.auth_type = ''github''
        ON CONFLICT (id) DO NOTHING;

        -- delete backup record
        DELETE from integration_backup ib WHERE ib.auth_type = ''github'';

        -- drop table if empty
        IF NOT EXISTS (SELECT 1 FROM integration_backup) THEN
            DROP TABLE integration_backup;
        END IF;
    END IF;
END;
';
