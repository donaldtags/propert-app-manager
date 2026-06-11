# MariaDB Setup

The REST API endpoints stay the same. Only the database profile changes.

## Start MariaDB

```bash
docker compose up -d mariadb
```

This creates:

- database: `primenest_prop`
- root user: `root`
- root password: `root`
- root host access: `%`
- app user: `primenest`
- app password: `primenest_password`
- local port: `3307`

If MariaDB was previously started with a different root password or host access, recreate the local development volume so the credentials above are applied:

```bash
docker compose down -v
docker compose up -d mariadb
```

## Run The Backend With MariaDB

```bash
./mvnw spring-boot:run
```

If port `8081` is busy:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

MariaDB is now the default backend database. The `test` profile uses H2 only for automated tests.

## Tables Created

Hibernate creates and updates the MariaDB schema from the JPA entities. The main tables include:

- `users`
- `app_user_roles`
- `admin_access_requests`
- `auth_sessions`
- `password_reset_tokens`
- `properties`
- `property_media`
- `leases`
- `lease_documents`
- `escrow_transactions`
- `payments`
- `maintenance_requests`
- `conversations`
- `messages`
- `landlord_ratings`
- `reits`
- `investments`

Seed data is disabled by default. Insert data through the REST API, then check the tables below. If you are using the default profile, data goes to the temporary H2 in-memory database instead of MariaDB.

## Useful Checks

```bash
docker exec -it primenest-mariadb mariadb -uroot -proot primenest_prop
```

Inside MariaDB:

```sql
SHOW TABLES;
SELECT id, title, city, suburb, price FROM properties;
```
