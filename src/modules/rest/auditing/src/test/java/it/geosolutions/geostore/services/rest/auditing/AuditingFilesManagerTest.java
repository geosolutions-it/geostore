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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public final class AuditingFilesManagerTest {

    @Before
    public void before() {
        AuditingTestsUtils.initDirectory(AuditingTestsUtils.TESTS_ROOT_DIRECTORY);
        AuditingTestsUtils.createDefaultConfiguration();
        AuditingTestsUtils.initDirectory(AuditingTestsUtils.OUTPUT_DIRECTORY);
    }

    @AfterClass
    public static void after() {
        AuditingTestsUtils.deleteDirectory(AuditingTestsUtils.TESTS_ROOT_DIRECTORY);
    }

    @Test
    public void testCleanInit() {
        AuditingFilesManager auditingFilesManager =
                new AuditingFilesManager(AuditingTestsUtils.OUTPUT_DIRECTORY.getPath(), "txt");
        File expectedOutputFile = new File(AuditingTestsUtils.OUTPUT_DIRECTORY, "audit-geostore.txt");
        Assert.assertEquals(auditingFilesManager.getOutputFile(), expectedOutputFile);
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY, expectedOutputFile);
    }

    @Test
    public void testInitWithExistingFiles() {
        AuditingTestsUtils.createFile(
                new File(AuditingTestsUtils.OUTPUT_DIRECTORY, "audit-geostore.txt"),
                "existing1");
        AuditingFilesManager auditingFilesManager =
                new AuditingFilesManager(AuditingTestsUtils.OUTPUT_DIRECTORY.getPath(), "txt");
        File expectedOutputFile = new File(AuditingTestsUtils.OUTPUT_DIRECTORY, "audit-geostore.txt");
        File expectedExistingFile = new File(AuditingTestsUtils.OUTPUT_DIRECTORY,
                String.format("audit-geostore-%s-1.txt", auditingFilesManager.getCurrentDayTag()));
        Assert.assertEquals(auditingFilesManager.getOutputFile(), expectedOutputFile);
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY,
                expectedOutputFile, expectedExistingFile);
        AuditingTestsUtils.checkFileExistsWithContent(expectedExistingFile, "existing1");
        AuditingTestsUtils.checkFileExistsWithContent(expectedOutputFile, "");
    }

    @Test
    public void testRollingFiles() {
        AuditingFilesManager auditingFilesManager =
                new AuditingFilesManager(AuditingTestsUtils.OUTPUT_DIRECTORY.getPath(), "txt");
        File expectedOutputFile = new File(AuditingTestsUtils.OUTPUT_DIRECTORY, "audit-geostore.txt");
        Assert.assertEquals(auditingFilesManager.getOutputFile(), expectedOutputFile);
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY, expectedOutputFile);
        AuditingTestsUtils.writeToFile(expectedOutputFile, "rolled1");
        auditingFilesManager.rollOutputFile();
        File expectedExistingFile1 = new File(AuditingTestsUtils.OUTPUT_DIRECTORY,
                String.format("audit-geostore-%s-1.txt", auditingFilesManager.getCurrentDayTag()));
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY,
                expectedOutputFile, expectedExistingFile1);
        AuditingTestsUtils.checkFileExistsWithContent(expectedExistingFile1, "rolled1");
        AuditingTestsUtils.checkFileExistsWithContent(expectedOutputFile, "");
        String previousCurrentTagDay = auditingFilesManager.getCurrentDayTag();
        AuditingTestsUtils.writeToFile(expectedOutputFile, "rolled2");
        auditingFilesManager.rollOutputFile();
        int rolledFileIndex = previousCurrentTagDay.equals(auditingFilesManager.getCurrentDayTag()) ? 2 : 1;
        File expectedExistingFile2 = new File(AuditingTestsUtils.OUTPUT_DIRECTORY,
                String.format("audit-geostore-%s-%d.txt", auditingFilesManager.getCurrentDayTag(), rolledFileIndex));
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY,
                expectedOutputFile, expectedExistingFile1, expectedExistingFile2);
        AuditingTestsUtils.checkFileExistsWithContent(expectedExistingFile1, "rolled1");
        AuditingTestsUtils.checkFileExistsWithContent(expectedExistingFile2, "rolled2");
        AuditingTestsUtils.checkFileExistsWithContent(expectedOutputFile, "");
    }

    @Test
    public void testMakeFileExists() {
        AuditingFilesManager auditingFilesManager =
                new AuditingFilesManager(AuditingTestsUtils.OUTPUT_DIRECTORY.getPath(), "txt");
        File expectedOutputFile = new File(AuditingTestsUtils.OUTPUT_DIRECTORY, "audit-geostore.txt");
        Assert.assertEquals(auditingFilesManager.getOutputFile(), expectedOutputFile);
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY, expectedOutputFile);
        AuditingTestsUtils.deleteFile(expectedOutputFile);
        AuditingTestsUtils.checkDirectoryIsEmpty(AuditingTestsUtils.OUTPUT_DIRECTORY);
        auditingFilesManager.makeOutputFileExists();
        AuditingTestsUtils.checkDirectoryContainsFiles(AuditingTestsUtils.OUTPUT_DIRECTORY, expectedOutputFile);
    }
}
