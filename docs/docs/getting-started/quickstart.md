# Quickstart

!!! note "Placeholder"
    This page is a placeholder. Content will be added in a future update.

## Running GeoStore Locally

After building from source, you can run GeoStore with the embedded H2 database for quick testing:

```bash
cd src/web/app
mvn jetty:run
```

GeoStore will be available at `http://localhost:8080/geostore/rest/`.

## Default Credentials

| Username | Password | Role  |
|----------|----------|-------|
| `admin`  | `admin`  | ADMIN |
| `user`   | `user`   | USER  |

## Basic API Usage

```bash
# List resources
curl -u admin:admin http://localhost:8080/geostore/rest/resources

# Create a resource
curl -u admin:admin -X POST \
  -H "Content-Type: application/xml" \
  -d '<Resource><name>test</name><category><name>MAP</name></category></Resource>' \
  http://localhost:8080/geostore/rest/resources
```
