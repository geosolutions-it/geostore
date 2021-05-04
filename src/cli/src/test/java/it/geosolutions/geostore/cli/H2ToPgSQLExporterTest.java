/*
 * ====================================================================
 *
 * Copyright (C) 2021 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;

public abstract class H2ToPgSQLExporterTest {
    protected H2ToPgSQLExporter exporter;
    @Before
    public void setUp() {
        exporter = new H2ToPgSQLExporter();
        exporter.username = "geostore";
        exporter.password = "geostore";
    }
    
    protected String getInvalidDbPath() {
        return "WRONGPATH";
    }
    protected String getTestDbPath() throws IOException {
        File tempFile = File.createTempFile("geostore", ".h2.db");
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }
    

    protected String getTestDbPathWithoutExtension() throws IOException {
        String path = getTestDbPath();
        return path.substring(0, path.indexOf("."));
    }
    
    
    protected String getTestDb() throws IOException {
        File tempFile = File.createTempFile("geostore", ".h2.db");
        tempFile.delete();
        Files.copy(H2ToPgSQLExporterScriptTest.class.getResourceAsStream("geostore.h2.db"), Paths.get(tempFile.getAbsolutePath()));
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }
    
    protected String getInvalidDb() throws IOException {
        File tempFile = File.createTempFile("geostore", ".h2.db");
        tempFile.delete();
        Files.copy(H2ToPgSQLExporterScriptTest.class.getResourceAsStream("geostore_invalid.h2.db"), Paths.get(tempFile.getAbsolutePath()));
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }
}
