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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Optional;
import org.junit.Test;

public class H2ToPgSQLExporterInputTest extends H2ToPgSQLExporterTest {
    
    @Test
    public void validInputPath() throws IOException {
        exporter.inputPath = getTestDbPath(); 

        Optional<String> path = exporter.validateInputFile();

        assertTrue(path.isPresent());
        assertFalse(path.get().endsWith(".h2.db"));
    }
    
    @Test
    public void inputPathWithoutExtension() throws IOException {
        exporter.inputPath = getTestDbPathWithoutExtension(); 

        Optional<String> path = exporter.validateInputFile();

        assertTrue(path.isPresent());
    }
    
    @Test
    public void invalidInputPath() {
        exporter.inputPath = getInvalidDbPath(); 

        Optional<String> path = exporter.validateInputFile();

        assertFalse(path.isPresent());
    }
}
