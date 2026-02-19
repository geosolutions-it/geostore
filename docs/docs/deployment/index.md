# Deployment Guide

!!! note "Placeholder"
    This page is a placeholder. Content will be added in a future update.

## Deployment Options

GeoStore can be deployed as a standard Java WAR file in any Servlet 3.1+ container:

- **Apache Tomcat 9+** (recommended)
- **Jetty 9+**

## Database Support

GeoStore uses JPA/Hibernate and supports:

- **H2** — Embedded database for development and testing
- **PostgreSQL** — Recommended for production
- **Oracle** — Supported via JDBC

## Production Checklist

- [ ] Configure a production database (PostgreSQL recommended)
- [ ] Set up TLS/HTTPS (required for OAuth2)
- [ ] Configure identity provider integration (see [Security](../security/index.md))
- [ ] Review session timeout and cache settings
- [ ] Set up backup and monitoring
