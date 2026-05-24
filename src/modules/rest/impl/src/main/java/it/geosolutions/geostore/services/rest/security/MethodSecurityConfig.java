/* ====================================================================
 *
 * Copyright (C) 2026 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 */
package it.geosolutions.geostore.services.rest.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables Spring Security method-level authorization for {@code @Secured} (and
 * {@code @RolesAllowed}) annotations on JAX-RS REST endpoints.
 *
 * <p>Replaces the {@code <security:method-security secured-enabled="true"/>} XML element that was
 * present on master and on early spring7. The XML form coexists problematically with overlay Java
 * configs in Spring Security 7 (a Java {@code @EnableMethodSecurity} would silently take precedence
 * over the XML-declared interceptor); centralising the declaration here removes that foot-gun.
 */
@Configuration
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class MethodSecurityConfig {}
