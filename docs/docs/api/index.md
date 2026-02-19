# REST API Reference

!!! note "Placeholder"
    This page is a placeholder. Content will be added in a future update.

## Overview

GeoStore exposes a RESTful API for managing resources, categories, users, and user groups. The API supports both XML and JSON content types.

## Base URL

```
http://<host>:<port>/geostore/rest/
```

## Main Endpoints

| Endpoint | Description |
|----------|-------------|
| `/resources` | CRUD operations on resources |
| `/categories` | Category management |
| `/users` | User management |
| `/usergroups` | User group management |
| `/session` | Session management and token operations |
| `/misc` | Miscellaneous operations (e.g., reload configuration) |

## Authentication

All endpoints support HTTP Basic authentication and OAuth2 Bearer token authentication. See the [Security section](../security/index.md) for details.
