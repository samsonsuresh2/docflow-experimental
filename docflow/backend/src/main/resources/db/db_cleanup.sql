BEGIN
  FOR obj IN (
    SELECT object_name, object_type
    FROM user_objects
    WHERE object_type IN (
      'TABLE', 'VIEW', 'SEQUENCE', 'TRIGGER',
      'PROCEDURE', 'FUNCTION', 'PACKAGE'
    )
  ) LOOP
    BEGIN
      IF obj.object_type = 'TABLE' THEN
        EXECUTE IMMEDIATE 'DROP TABLE ' || obj.object_name || ' CASCADE CONSTRAINTS PURGE';
      ELSIF obj.object_type = 'VIEW' THEN
        EXECUTE IMMEDIATE 'DROP VIEW ' || obj.object_name;
      ELSIF obj.object_type = 'SEQUENCE' THEN
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || obj.object_name;
      ELSIF obj.object_type = 'TRIGGER' THEN
        EXECUTE IMMEDIATE 'DROP TRIGGER ' || obj.object_name;
      ELSIF obj.object_type = 'PROCEDURE' THEN
        EXECUTE IMMEDIATE 'DROP PROCEDURE ' || obj.object_name;
      ELSIF obj.object_type = 'FUNCTION' THEN
        EXECUTE IMMEDIATE 'DROP FUNCTION ' || obj.object_name;
      ELSIF obj.object_type = 'PACKAGE' THEN
        EXECUTE IMMEDIATE 'DROP PACKAGE ' || obj.object_name;
      END IF;
    EXCEPTION
      WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('Failed: ' || obj.object_type || ' ' || obj.object_name || ' -> ' || SQLERRM);
    END;
  END LOOP;
END;