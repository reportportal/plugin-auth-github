DO
'
BEGIN
IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ''integration_backup'') THEN

    -- restore github integration from backup
    INSERT INTO integration (name, type, params, creator, creation_date, enabled)
    SELECT ib.name,
            (SELECT id
        FROM integration_type
        WHERE name = ''github'' AND group_type = ''AUTH'' AND plugin_type = ''EXTENSION''),
        ib.params,
        ''SYSTEM'',
        now(),
        true
    FROM integration_backup ib
    ON CONFLICT (id) DO NOTHING;

    -- delete backup record
    DELETE from integration_backup ib WHERE ib.name = ''github'' and ib.auth_type = ''OAUTH'';

    -- drop table if empty
    IF NOT EXISTS (SELECT 1 FROM integration_backup) THEN
        DROP TABLE integration_backup;
        END IF;
    END IF;
END;
';
