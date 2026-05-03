CREATE USER airflow WITH PASSWORD 'airflow';
CREATE DATABASE airflow OWNER airflow;

CREATE USER marquez WITH PASSWORD 'marquez';
CREATE DATABASE marquez OWNER marquez;

CREATE DATABASE state_storage OWNER postgres;
CREATE DATABASE observability_admin OWNER postgres;
