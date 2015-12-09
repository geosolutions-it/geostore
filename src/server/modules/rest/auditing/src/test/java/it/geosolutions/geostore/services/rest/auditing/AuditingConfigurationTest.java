/* ====================================================================
 *
 * Copyright (C) 2007 - 2015 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.auditing;

import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public final class AuditingConfigurationTest {

    @Before
    public void before() {
        AuditingTestsUtils.initDirectory(AuditingTestsUtils.TESTS_ROOT_DIRECTORY);
        AuditingTestsUtils.createDefaultConfiguration();
    }

    @AfterClass
    public static void after() {
        AuditingTestsUtils.deleteDirectory(AuditingTestsUtils.TESTS_ROOT_DIRECTORY);
    }

    @Test
    public void testSimpleConfiguration() {
        AuditingConfiguration auditingConfiguration = new AuditingConfiguration();
        Assert.assertEquals(auditingConfiguration.isAuditEnable(), true);
        Assert.assertEquals(auditingConfiguration.getMaxRequestPerFile(), 3);
        Assert.assertEquals(auditingConfiguration.getTemplatesVersion(), 1);
        Assert.assertEquals(auditingConfiguration.getOutputDirectory(), AuditingTestsUtils.OUTPUT_DIRECTORY.getAbsolutePath());
        Assert.assertEquals(auditingConfiguration.getOutputFilesExtension(), "txt");
        Assert.assertEquals(auditingConfiguration.getTemplatesDirectory(), AuditingTestsUtils.TEMPLATES_DIRECTORY.getAbsolutePath());
    }

    @Test
    public void testUpdateConfiguration() {
        AuditingConfiguration auditingConfiguration = new AuditingConfiguration();
        Assert.assertEquals(auditingConfiguration.isAuditEnable(), true);
        Map<String, String> properties = AuditingTestsUtils.getDefaultProperties();
        properties.put(AuditingConfiguration.AUDIT_ENABLE, "false");
        AuditingTestsUtils.createFile(AuditingTestsUtils.CONFIGURATION_FILE_PATH,
                AuditingTestsUtils.propertiesToString(properties));
        AuditingConfiguration newAuditingConfiguration = auditingConfiguration.checkForNewConfiguration();
        Assert.assertNotNull(newAuditingConfiguration);
    }
}
