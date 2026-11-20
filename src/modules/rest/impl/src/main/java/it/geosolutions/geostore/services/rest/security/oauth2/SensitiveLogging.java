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
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.ExtendedLogger;

/**
 * Logging of token values, claim contents (roles, groups, userinfo and introspection results) and
 * provider settings. They are written at DEBUG and only for a provider configured with {@code
 * logSensitiveInfo=true}, which also turns DEBUG on for the security loggers. Raising the log level
 * alone does not print them.
 */
public final class SensitiveLogging {

    private static final String FQCN = SensitiveLogging.class.getName();

    private SensitiveLogging() {}

    /** True when the provider has {@code logSensitiveInfo} enabled. */
    public static boolean enabled(IdPConfiguration configuration) {
        return configuration instanceof OAuth2Configuration
                && ((OAuth2Configuration) configuration).isLogSensitiveInfo();
    }

    /** Logs at DEBUG when the provider has {@code logSensitiveInfo} enabled, otherwise nothing. */
    public static void debug(
            Logger logger, IdPConfiguration configuration, String message, Object... args) {
        if (!enabled(configuration)) return;
        if (logger instanceof ExtendedLogger) {
            // keeps the caller, not this class, as the location shown in the log
            ((ExtendedLogger) logger).logIfEnabled(FQCN, Level.DEBUG, null, message, args);
        } else {
            logger.debug(message, args);
        }
    }
}
