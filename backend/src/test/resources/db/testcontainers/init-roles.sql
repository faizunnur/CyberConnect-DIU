-- Mirrors infrastructure/postgres/init/01-roles.sh for Testcontainers: the runtime role exists before Flyway runs.
CREATE ROLE cyberconnect_app LOGIN PASSWORD 'app-test-password';
GRANT CONNECT ON DATABASE cyberconnect TO cyberconnect_app;
GRANT USAGE ON SCHEMA public TO cyberconnect_app;
