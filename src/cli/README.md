GeoStore Command Line utilities
========
The GeoStore CLI module includes command line utilites to interact with GeoStore and its internal database.

It currently includes the following utilities:
 - **H2ToPgSQLExport**: allows migrating the GeoStore database from H2 to PostgreSQL.

Building
--------
The standard GeoStore build does not include the CLI module, but it is possible to enable specific profiles to:

 - build the CLI module alone: you can run the maven build with the dedicated *cli* profile.

```bash
mvn install -P cli
```

 - run the full build and include also the CLI module: you can run the maven build with the dedicated *all* profile.

```bash
mvn install -P all
```

**Note**: you can also include the optional features, adding more profiles, eg.

```bash
mvn install -P all,postgres,extjs
```

The final artifacts of the build will be:

 * the H2toPgSQLExport tool, an executable jar, that includes all the needed dependencies, located in:
```
src/cli/target/H2ToPgSQLExport.jar
```

H2ToPgSQLExport
---------------
This tool can be used to migrate data from a GeoStore H2 database file, to a PostgreSQL database.

We always advice using a full fledged database (like PosgreSQL or Oracle) in production, while an H2 embedded database can be useful during development or testing.

Sometimes migrating a development database into production is therefore needed.

The tool produces an SQL script that can be run on the destination database to import the data to migrate from an H2 geostore database file.

The destination database must be:
 * already populated with the geostore schema and the related tables
 * all the tables should be empty, no data removal will be done by the produced scripts

### Usage
To get a migration script, run the utility with the following parameters:

```bash
java -jar H2ToPgSQLExport.jar [-o[=<outputPath>]] [-p=<password>] [-u=<username>] H2FILE
```

 * **H2FILE**: path to the H2 database file to migrate
 * **outputPath**: path to output SQL file, if missing the output is written to the standard output
 * **password**: H2 database password, if missing, the default password (geostore) is used
 * **username**: H2 database username, if missing, the default username (geostore) is used

To import the data into an empty PostgreSQL database, using the generated script, you can use the PostgreSQL psql tool as follows:

```bash
# creates the geostore user role and schemas
psql -U <admin_user> -d <db_name> -W -f <geostore_src_root>/doc/sql/001_setup_db.sql

# creates the geostore tables in the geostore schema
psql -U geostore -d <db_name> -W -f <geostore_src_root>/doc/sql/002_create_schema_postgres.sql

# imports data exported from H2
psql -U geostore -d <db_name> -W -f <exported_sql_script>
```

### Connect GeoStore to the new database

Read the [DBMS configuration docs](https://github.com/geosolutions-it/geostore/wiki/Configure-the-DBMS) to learn how to connect your new PostgreSQL database to GeoStore.
