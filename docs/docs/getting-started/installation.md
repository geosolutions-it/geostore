# Installation

## Prerequisites

Before installing GeoStore, make sure you have:

- **Java 11** or later (JDK required for building from source; JRE sufficient for running a pre-built WAR).
- **Apache Maven 3.6+** (only needed if building from source).
- **A servlet container** — Apache Tomcat 9+ (recommended) or Jetty 9+.
- **A supported database** — H2 (embedded, for development), PostgreSQL (recommended for production), or Oracle.

!!! tip
    For a quick evaluation you can skip the external database entirely — GeoStore ships with an embedded H2 database that is used by default.

---

## Building from Source

### Clone the Repository

```bash
git clone https://github.com/geosolutionsgroup/geostore.git
cd geostore
```

### Build with Maven

The default build uses the embedded H2 database driver:

```bash
mvn clean install
```

To include a production database driver, activate the corresponding Maven profile:

=== "PostgreSQL"

    ```bash
    mvn clean install -Ppostgres
    ```

=== "Oracle"

    ```bash
    mvn clean install -Poracle
    ```

!!! note
    The `-Ppostgres` and `-Poracle` profiles add the respective JDBC driver to the WAR. You still need to configure the connection properties at runtime (see [Configuration](configuration.md)).

### Build Output

After a successful build the deployable WAR is located at:

```
src/web/app/target/geostore-webapp.war
```

---

## WAR Deployment

### Apache Tomcat

1. Copy the WAR file into Tomcat's `webapps/` directory:

    ```bash
    cp src/web/app/target/geostore-webapp.war $CATALINA_HOME/webapps/geostore.war
    ```

2. Start (or restart) Tomcat:

    ```bash
    $CATALINA_HOME/bin/startup.sh
    ```

3. Verify the deployment by visiting:

    ```
    http://localhost:8080/geostore/rest/users/user/details
    ```

    You should receive a `401 Unauthorized` response (or user details if you pass credentials), confirming the application is running.

### Jetty

For development or lightweight deployments, you can run GeoStore directly with the Maven Jetty plugin:

```bash
cd src/web/app
mvn jetty:run
```

GeoStore will be available at `http://localhost:8080/geostore/rest/`.

---

## Database Setup

### H2 (Default — Development Only)

No setup is required. GeoStore uses an in-memory H2 database out of the box. Data is lost when the application stops.

### PostgreSQL (Recommended for Production)

1. Create a database and user:

    ```sql
    CREATE USER geostore WITH PASSWORD 'geostore';
    CREATE DATABASE geostore OWNER geostore;
    ```

2. Configure the datasource in your override properties file (see [Configuration](configuration.md)):

    ```properties
    geostoreDataSource.driverClassName=org.postgresql.Driver
    geostoreDataSource.url=jdbc:postgresql://localhost:5432/geostore
    geostoreDataSource.username=geostore
    geostoreDataSource.password=geostore
    geostoreEntityManagerFactory.jpaPropertyMap[hibernate.dialect]=org.hibernate.dialect.PostgreSQLDialect
    geostoreEntityManagerFactory.jpaPropertyMap[hibernate.hbm2ddl.auto]=update
    ```

3. On first startup with `hibernate.hbm2ddl.auto=update`, Hibernate will create the required tables automatically.

### Oracle

1. Create a tablespace and user:

    ```sql
    CREATE USER geostore IDENTIFIED BY geostore;
    GRANT CONNECT, RESOURCE TO geostore;
    ```

2. Configure the datasource:

    ```properties
    geostoreDataSource.driverClassName=oracle.jdbc.OracleDriver
    geostoreDataSource.url=jdbc:oracle:thin:@localhost:1521:orcl
    geostoreDataSource.username=geostore
    geostoreDataSource.password=geostore
    geostoreEntityManagerFactory.jpaPropertyMap[hibernate.dialect]=org.hibernate.dialect.Oracle10gDialect
    geostoreEntityManagerFactory.jpaPropertyMap[hibernate.hbm2ddl.auto]=update
    ```

---

## Initial Users

On first startup, GeoStore loads seed data from XML files bundled in the WAR:

| Username | Password | Role       |
|----------|----------|------------|
| `admin`  | `admin`  | ADMIN      |
| `user`   | `user`   | USER       |

!!! warning
    Change the default passwords immediately in any non-development environment. You can update passwords via the REST API or by editing `sample_users.xml` before building.

---

## Verifying the Installation

```bash
# Health check — should return user details (HTTP 200)
curl -u admin:admin http://localhost:8080/geostore/rest/users/user/details

# List categories
curl -u admin:admin http://localhost:8080/geostore/rest/categories

# List resources
curl -u admin:admin http://localhost:8080/geostore/rest/resources
```

---

## Next Steps

- [Quickstart](quickstart.md) — Create your first resources and explore the API.
- [Configuration](configuration.md) — Customize properties, security, and datasource settings.
- [Security Overview](../security/index.md) — Set up OIDC, LDAP, or bearer token authentication.
- [Deployment Guide](../deployment/index.md) — Production hardening and operational guidance.
