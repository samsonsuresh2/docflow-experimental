DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES WHERE TABLE_NAME = 'USER_ROLE_MAP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE USER_ROLE_MAP (
                USER_ID    VARCHAR2(200) NOT NULL,
                ROLE       VARCHAR2(50)  NOT NULL,
                ENABLED    CHAR(1)       DEFAULT ''Y'',
                CREATED_AT TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                UPDATED_AT TIMESTAMP,
                CONSTRAINT PK_USER_ROLE_MAP PRIMARY KEY (USER_ID, ROLE)
            )';
    END IF;
END;
/

DECLARE
    PROCEDURE seed_role(p_user VARCHAR2, p_role VARCHAR2) IS
        v_exists INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM USER_ROLE_MAP WHERE USER_ID = p_user AND ROLE = p_role;
        IF v_exists = 0 THEN
            INSERT INTO USER_ROLE_MAP (USER_ID, ROLE, ENABLED) VALUES (p_user, p_role, 'Y');
        END IF;
    END;
BEGIN
    seed_role('samson', 'ADMIN');
    seed_role('samson', 'APPROVER');
    seed_role('samson', 'REVIEWER');
END;
/
