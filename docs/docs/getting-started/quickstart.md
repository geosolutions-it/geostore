# Quickstart

This guide walks you through starting GeoStore locally, creating resources, and exercising the REST API.

---

## Running GeoStore Locally

After [building from source](installation.md), start GeoStore with the embedded Jetty server and H2 database:

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

---

## Basic API Usage

All examples use `curl` with HTTP Basic authentication. The API accepts and returns both XML and JSON — add `-H "Content-Type: application/json"` and `-H "Accept: application/json"` to use JSON.

### Create a Category

Categories group related resources. Create one before adding resources:

```bash
curl -u admin:admin -X POST \
  -H "Content-Type: application/xml" \
  -d '<Category><name>MAP</name></Category>' \
  http://localhost:8080/geostore/rest/categories
```

The response body contains the new category ID.

### Create a Resource

```bash
curl -u admin:admin -X POST \
  -H "Content-Type: application/xml" \
  -d '<Resource>
        <name>my-first-map</name>
        <description>A sample map resource</description>
        <category><name>MAP</name></category>
        <store><data>{"mapId": 1, "title": "Hello World"}</data></store>
      </Resource>' \
  http://localhost:8080/geostore/rest/resources
```

### List Resources

```bash
# All resources (paginated)
curl -u admin:admin http://localhost:8080/geostore/rest/resources

# Search by name
curl -u admin:admin http://localhost:8080/geostore/rest/resources/search/my-first
```

### Get a Single Resource

```bash
# Replace {id} with the ID returned during creation
curl -u admin:admin http://localhost:8080/geostore/rest/resources/resource/{id}
```

### Get the Stored Data

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/data/{id}
```

### Update a Resource

```bash
curl -u admin:admin -X PUT \
  -H "Content-Type: application/xml" \
  -d '<Resource><description>Updated description</description></Resource>' \
  http://localhost:8080/geostore/rest/resources/resource/{id}
```

### Delete a Resource

```bash
curl -u admin:admin -X DELETE \
  http://localhost:8080/geostore/rest/resources/resource/{id}
```

---

## Working with JSON

The same operations work with JSON content:

```bash
# Create a resource using JSON
curl -u admin:admin -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "Resource": {
      "name": "json-map",
      "description": "Created with JSON",
      "category": {"name": "MAP"},
      "store": {"data": "{\"mapId\": 2}"}
    }
  }' \
  http://localhost:8080/geostore/rest/resources
```

---

## Working with Attributes

Resources can have typed key-value attributes for metadata:

```bash
# Set an attribute
curl -u admin:admin -X PUT \
  http://localhost:8080/geostore/rest/resources/resource/{id}/attributes/owner/admin

# Get all attributes
curl -u admin:admin \
  http://localhost:8080/geostore/rest/resources/resource/{id}/attributes
```

---

## Security Rules

Control per-resource access by setting security rules:

```bash
curl -u admin:admin -X POST \
  -H "Content-Type: application/xml" \
  -d '<SecurityRuleList>
        <SecurityRule>
          <canRead>true</canRead>
          <canWrite>false</canWrite>
          <user><name>user</name></user>
        </SecurityRule>
      </SecurityRuleList>' \
  http://localhost:8080/geostore/rest/resources/resource/{id}/permissions
```

---

## Using Bearer Tokens

If you have OIDC configured, you can authenticate with Bearer tokens instead of Basic auth:

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/geostore/rest/resources
```

See [Bearer Token Authentication](../security/bearer-tokens.md) for setup details.

---

## Next Steps

- [Configuration](configuration.md) — Customize database, security, and application settings.
- [REST API Reference](../api/index.md) — Full endpoint documentation.
- [Security Overview](../security/index.md) — Configure OIDC, LDAP, or header-based SSO.
