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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import it.geosolutions.geostore.cli.H2ToPgSQLExporter.Output;
import it.geosolutions.geostore.cli.H2ToPgSQLExporter.OutputType;

public class H2ToPgSQLExporterOutputTest extends H2ToPgSQLExporterTest {
    @Test
    public void existingFolder() throws IOException {
        exporter.outputPath = getValidPath();
        
        Output output = exporter.validateOutputFile();
        
        assertTrue(output.type == OutputType.FILE);
        assertEquals(exporter.outputPath, output.path.get());
    }
    
    @Test
    public void pathWithoutExtension() throws IOException {
        exporter.outputPath = getValidPathWithoutExtension();
        
        Output output = exporter.validateOutputFile();
        
        assertTrue(output.type == OutputType.FILE);
        assertTrue(output.path.get().toLowerCase().endsWith(".sql"));
    }
    
    @Test
    public void notExistingFolder() throws IOException {
        exporter.outputPath = getInvalidPath();
        
        Output output = exporter.validateOutputFile();
        
        assertTrue(output.type == OutputType.INVALID);
    }
    
    @Test
    public void standardOutput() throws IOException {
        exporter.outputPath = null;
        
        Output output = exporter.validateOutputFile();
        
        assertTrue(output.type == OutputType.STDOUT);
    }

    private String getValidPath() throws IOException {
        File tempFile = File.createTempFile("output", ".sql");
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }
    
    private String getValidPathWithoutExtension() throws IOException {
        File tempFile = File.createTempFile("output", "");
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }
    
    private String getInvalidPath() throws IOException {
        File tempFolder = File.createTempFile("output_folder", "");
        File tempFile = new File(tempFolder.getAbsolutePath() + File.separator + "output.sql");
        tempFolder.delete();
        return tempFile.getAbsolutePath();
    }
}
