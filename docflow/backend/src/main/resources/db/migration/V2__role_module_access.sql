DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES WHERE TABLE_NAME = 'ROLE_MODULE_ACCESS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ROLE_MODULE_ACCESS (
                ROLE        VARCHAR2(50)   NOT NULL,
                MODULE_CODE VARCHAR2(100)  NOT NULL,
                ENABLED     CHAR(1)        DEFAULT ''Y'',
                CREATED_AT  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
                UPDATED_AT  TIMESTAMP,
                CONSTRAINT PK_ROLE_MODULE_ACCESS PRIMARY KEY (ROLE, MODULE_CODE)
            )';
    END IF;
END;
/

DECLARE
    PROCEDURE seed_access(p_role VARCHAR2, p_module VARCHAR2) IS
        v_exists INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ROLE_MODULE_ACCESS WHERE ROLE = p_role AND MODULE_CODE = p_module;
        IF v_exists = 0 THEN
            INSERT INTO ROLE_MODULE_ACCESS (ROLE, MODULE_CODE, ENABLED) VALUES (p_role, p_module, 'Y');
        END IF;
    END;
BEGIN
    seed_access('MAKER', 'UPLOAD');
    seed_access('MAKER', 'REPORTS');
    seed_access('REVIEWER', 'UPLOAD');
    seed_access('REVIEWER', 'REVIEW');
    seed_access('APPROVER', 'REVIEW');
    seed_access('APPROVER', 'AUDIT');
    seed_access('ADMIN', 'UPLOAD');
    seed_access('ADMIN', 'REPORT_CONFIG');
    seed_access('ADMIN', 'AUDIT');
    seed_access('ADMIN', 'REPORTS');
    seed_access('ADMIN', 'DATA_INGEST');
    seed_access('ADMIN', 'ADMIN');
END;
/

DECLARE
    v_exists INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_ROLES WHERE USER_ID = 'approver1' AND ROLE_NAME = 'APPROVER';
    IF v_exists = 0 THEN
        INSERT INTO USER_ROLES (id, user_id, role_name, assigned_at)
        VALUES (user_roles_seq.NEXTVAL, 'approver1', 'APPROVER', SYSTIMESTAMP);
    END IF;
END;
/
