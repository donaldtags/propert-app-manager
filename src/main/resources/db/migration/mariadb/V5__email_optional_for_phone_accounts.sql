-- Accounts can sign up with only a phone number. MariaDB unique indexes allow many NULLs.
ALTER TABLE users MODIFY COLUMN email VARCHAR(255) NULL;
