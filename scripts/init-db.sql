-- Ten skrypt odpali się przy starcie kontenera PostgreSQL (jako superuser).
-- smartfin_db tworzy się automatycznie z POSTGRES_DB — tu dodajemy brakującą bazę testową.
CREATE DATABASE smartfin_test OWNER finuser;
GRANT ALL PRIVILEGES ON DATABASE smartfin_test TO finuser;
