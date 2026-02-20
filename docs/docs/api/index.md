# REST API Reference

## Overview

GeoStore exposes a RESTful API for managing resources, categories, users, user groups, and related entities. The API supports both **XML** and **JSON** content types and uses standard HTTP methods (GET, POST, PUT, DELETE).

### Base URL

```
http://<host>:<port>/geostore/rest/
```

### Content Negotiation

Set the `Content-Type` and `Accept` headers to control request and response formats:

| Format | Content-Type |
|--------|-------------|
| XML    | `application/xml` or `text/xml` |
| JSON   | `application/json` |

### Authentication

All endpoints support:

- **HTTP Basic** — `Authorization: Basic <base64(user:pass)>`
- **Bearer Token** — `Authorization: Bearer <access_token>`
- **Session Token** — Obtained via the `/rest/session/login` endpoint.

See the [Security section](../security/index.md) for configuration details.

### Pagination

Endpoints that return lists accept optional pagination parameters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `page`    | int  | Page number (0-based) |
| `entries` | int  | Number of entries per page |

### Error Responses

Errors return standard HTTP status codes with a JSON body:

| Status | Meaning |
|--------|---------|
| 400    | Bad request — invalid input or missing required fields |
| 401    | Unauthorized — missing or invalid credentials |
| 403    | Forbidden — insufficient permissions |
| 404    | Not found — entity does not exist |
| 409    | Conflict — duplicate name or constraint violation |
| 500    | Internal server error |

401 responses include a structured JSON body:

```json
{
  "error": "unauthorized",
  "message": "Bearer token validation failed: token expired"
}
```

---

## Resources

**Base path:** `/rest/resources`

Resources are the primary data entities in GeoStore. Each resource has a name, belongs to a category, and can carry attributes, stored data, and security rules.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/resources` | Create a new resource | USER |
| GET | `/resources` | List all resources (paginated) | ANY |
| GET | `/resources/resource/{id}` | Get a resource by ID | ANY |
| PUT | `/resources/resource/{id}` | Update a resource | USER |
| DELETE | `/resources/resource/{id}` | Delete a resource | USER |
| DELETE | `/resources` | Delete resources by filter | ADMIN |
| GET | `/resources/search/{nameLike}` | Search resources by name pattern | ANY |
| POST | `/resources/search/list` | Advanced search with filter | ANY |
| GET | `/resources/count/{nameLike}` | Count matching resources | ANY |

### Attributes

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/resources/resource/{id}/attributes` | List all attributes for a resource | ANY |
| GET | `/resources/resource/{id}/attributes/{name}` | Get a single attribute value | ANY |
| PUT | `/resources/resource/{id}/attributes/` | Update attributes (JSON body) | USER |
| PUT | `/resources/resource/{id}/attributes/{name}/{value}` | Set attribute via path | USER |

### Security Rules

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/resources/resource/{id}/permissions` | Get security rules for a resource | USER |
| POST | `/resources/resource/{id}/permissions` | Update security rules | USER |

### Examples

=== "Create (XML)"

    ```bash
    curl -u admin:admin -X POST \
      -H "Content-Type: application/xml" \
      -d '<Resource>
            <name>my-map</name>
            <description>A map configuration</description>
            <category><name>MAP</name></category>
            <store><data>{"mapId": 1}</data></store>
          </Resource>' \
      http://localhost:8080/geostore/rest/resources
    ```

=== "Create (JSON)"

    ```bash
    curl -u admin:admin -X POST \
      -H "Content-Type: application/json" \
      -d '{
        "Resource": {
          "name": "my-map",
          "description": "A map configuration",
          "category": {"name": "MAP"},
          "store": {"data": "{\"mapId\": 1}"}
        }
      }' \
      http://localhost:8080/geostore/rest/resources
    ```

---

## Stored Data

**Base path:** `/rest/data`

Each resource can have associated stored data (typically a JSON or XML blob).

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/data/{id}` | Get stored data for a resource | ANY |
| GET | `/data/{id}/raw` | Get raw data with optional decoding | ANY |
| PUT | `/data/{id}` | Update stored data | USER |
| DELETE | `/data/{id}` | Delete stored data | USER |

The `raw` endpoint accepts a `decode` query parameter for Base64-encoded data.

---

## Categories

**Base path:** `/rest/categories`

Categories organise resources into logical groups (e.g., MAP, DASHBOARD, CONTEXT).

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/categories` | Create a new category | ADMIN |
| GET | `/categories` | List all categories (paginated) | ANY |
| GET | `/categories/category/{id}` | Get a category by ID | ANY |
| PUT | `/categories/category/{id}` | Update a category | ADMIN |
| DELETE | `/categories/category/{id}` | Delete a category | ADMIN |
| GET | `/categories/count/{nameLike}` | Count matching categories | ANY |

---

## Users

**Base path:** `/rest/users`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/users` | Create a new user | ADMIN |
| GET | `/users` | List all users (paginated) | ADMIN |
| GET | `/users/user/{id}` | Get a user by ID | ADMIN |
| GET | `/users/search/{name}` | Get a user by username | ADMIN |
| GET | `/users/search/list/{nameLike}` | Search users by name | ADMIN |
| PUT | `/users/user/{id}` | Update a user | ADMIN / self |
| DELETE | `/users/user/{id}` | Delete a user | ADMIN |
| GET | `/users/user/details` | Get the authenticated user's details | USER |
| GET | `/users/count/{nameLike}` | Count matching users | ANY |

### Favorites

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/users/user/{userId}/favorite/{resourceId}` | Add a resource to favorites | USER |
| DELETE | `/users/user/{userId}/favorite/{resourceId}` | Remove a resource from favorites | USER |

---

## User Groups

**Base path:** `/rest/usergroups`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/usergroups` | Create a new group | ADMIN |
| GET | `/usergroups` | List groups (with optional filters) | ADMIN |
| GET | `/usergroups/group/{id}` | Get a group by ID | ADMIN |
| GET | `/usergroups/group/name/{name}` | Get a group by name | ADMIN |
| PUT | `/usergroups/group/{id}` | Update a group | ADMIN |
| DELETE | `/usergroups/group/{id}` | Delete a group | ADMIN |
| POST | `/usergroups/group/{userId}/{groupId}` | Assign a user to a group | ADMIN |
| DELETE | `/usergroups/group/{userId}/{groupId}` | Remove a user from a group | ADMIN |
| PUT | `/usergroups/update_security_rules/{groupId}/{canRead}/{canWrite}` | Bulk-update security rules for group resources | ADMIN |
| GET | `/usergroups/search/attribute/{name}/{value}` | Search groups by attribute | ADMIN |

The list endpoint accepts optional query parameters: `all` (boolean), `users` (boolean to include members), `nameLike` (filter pattern).

---

## Tags

**Base path:** `/rest/resources/tag`

Tags provide free-form labels for resources, independent of categories.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/resources/tag` | Create a new tag | ADMIN |
| GET | `/resources/tag` | List all tags (paginated, filterable by `nameLike`) | ANY |
| GET | `/resources/tag/{id}` | Get a tag by ID | ANY |
| PUT | `/resources/tag/{id}` | Update a tag | ADMIN |
| DELETE | `/resources/tag/{id}` | Delete a tag | ADMIN |
| POST | `/resources/tag/{tagId}/resource/{resourceId}` | Apply a tag to a resource | USER |
| DELETE | `/resources/tag/{tagId}/resource/{resourceId}` | Remove a tag from a resource | USER |

---

## Session

**Base path:** `/rest/session`

Manage user sessions and authentication tokens.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/session/login` | Authenticate and obtain a session token | ANY |
| DELETE | `/session/logout` | Log out and invalidate the session | USER |
| GET | `/session/user/{sessionId}` | Get the user for a session | USER |
| GET | `/session/username/{sessionId}` | Get the username for a session | USER |
| PUT | `/session` | Create a new session (with optional `expires`) | USER |
| POST | `/session/refreshToken` | Refresh an access token | USER |
| DELETE | `/session` | Clear all sessions | ADMIN |

### Session Login Example

```bash
# Obtain a session token
curl -u admin:admin -X POST \
  http://localhost:8080/geostore/rest/session/login

# Response:
# {
#   "access_token": "abc123...",
#   "refresh_token": "def456...",
#   "expires": 86400,
#   "token_type": "bearer"
# }
```

---

## Miscellaneous

**Base path:** `/rest/misc`

Convenience endpoints for looking up resources by category and name.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/misc/category/name/{category}/resource/name/{resource}/data` | Get resource data by category + resource name | ANY |
| GET | `/misc/category/name/{category}/resource/name/{resource}` | Get resource by category + resource name | ANY |
| GET | `/misc/category/name/{category}/resources` | List resources in a category | ANY |
| GET | `/misc/category/name/{category}/fullresources` | List full resources (with attributes/data) | ANY |
| GET | `/misc/reload/{service}` | Reload a service | ADMIN |

---

## ExtJS

**Base path:** `/rest/extjs`

These endpoints return data formatted for ExtJS grid/store consumption (used by MapStore and similar clients).

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/extjs/search/resource/{nameLike}` | Search resources | ANY |
| GET | `/extjs/search/category/{name}` | Get resources by category | ANY |
| GET | `/extjs/search/category/{name}/{resourceName}` | Get category resources by name | ANY |
| POST | `/extjs/search/list` | Advanced search with filters, sorting, tags, and favorites | ANY |
| GET | `/extjs/search/users/{nameLike}` | Search users | ADMIN |
| GET | `/extjs/search/groups/{nameLike}` | Search groups | ADMIN |
| GET | `/extjs/resource/{id}` | Get extended resource data | ANY |

The `search/list` endpoint accepts a filter body and query parameters: `start`, `limit`, `includeAttributes`, `includeData`, `includeTags`, `favoritesOnly`.

---

## Backup

**Base path:** `/rest/backup`

Export and import GeoStore data. All backup endpoints require ADMIN role.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/backup/full` | Create a full backup | ADMIN |
| PUT | `/backup/full/{token}` | Restore from a full backup | ADMIN |
| GET | `/backup/quick` | Create a quick (in-memory) backup | ADMIN |
| PUT | `/backup/quick` | Restore from a quick backup | ADMIN |

---

## IP Ranges

**Base path:** `/rest/ipranges`

Manage IP-based access rules.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/ipranges` | Create an IP range | ADMIN |
| GET | `/ipranges` | List all IP ranges | USER |
| GET | `/ipranges/{id}` | Get an IP range by ID | USER |
| PUT | `/ipranges/{id}` | Update an IP range | ADMIN |
| DELETE | `/ipranges/{id}` | Delete an IP range | ADMIN |

---

## Diagnostics

**Base path:** `/rest/diagnostics`

Runtime observability for the security subsystem. All endpoints require ADMIN role.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/diagnostics` | Full diagnostics report | ADMIN |
| GET | `/diagnostics/logging` | Current logging configuration | ADMIN |
| PUT | `/diagnostics/logging/{loggerName}/{level}` | Set log level for a logger | ADMIN |
| GET | `/diagnostics/cache` | Token cache statistics | ADMIN |
| GET | `/diagnostics/configuration` | Security configuration dump (secrets redacted) | ADMIN |

See [Monitoring & Auditing](../security/monitoring-and-auditing.md) for usage details.

---

## Identity Provider Login

**Base path:** `/rest/openid`

OAuth2/OIDC login flow endpoints.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/openid/{provider}/login` | Initiate login with an external IdP | ANY |
| GET | `/openid/{provider}/callback` | OAuth2 callback (used by the IdP) | ANY |
| GET | `/openid/{provider}/tokens` | Retrieve tokens by identifier | ANY |
| GET | `/openid/providers` | List available identity providers | ANY |

The `{provider}` path segment identifies the OIDC provider (e.g., `oidc`, `google`, `azure`). See [OIDC / OAuth2](../security/oidc.md) for setup.
