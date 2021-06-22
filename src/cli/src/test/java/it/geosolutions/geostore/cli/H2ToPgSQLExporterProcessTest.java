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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public class H2ToPgSQLExporterProcessTest extends H2ToPgSQLExporterTest {
    @Before
    public void setUp() {
        super.setUp();
        exporter.username = "geostore";
        exporter.password = "geostore";
    }
    
    @Test
    public void emptyListIfNoInsertsInScript() throws IOException {
        String script = getInvalidScript();
        
        List<String> sql = exporter.filterInserts(script);
        
        assertNotNull(sql);
        assertEquals(0, sql.size());
    }
    
    @Test
    public void onlyInsertsAreTakenFromScript() throws IOException {
        String script = getValidScript();
        
        List<String> sql = exporter.filterInserts(script);
        
        assertNotNull(sql);
        sql.forEach(s -> assertTrue(s.toUpperCase().startsWith("INSERT")));
    }
    
    @Test
    public void insertsAreSortedToAvoidConstraintsErrors() throws IOException {
        String script = getValidScript();
        
        List<String> sql = exporter.filterInserts(script);
        
        assertNotNull(sql);
        IntStream.range(0, exporter.orderedTables.size()).forEach(
                idx -> assertContains(sql.get(idx), exporter.orderedTables.get(idx)));
    }
    
    @Test
    public void dataIsStoredInTheGeoStoreSchema() throws IOException {
        String script = getValidScript();
        
        List<String> sql = exporter.filterInserts(script);
        
        sql.forEach(s -> 
            assertTrue(exporter.normalizeInsert(sql.get(0)).toUpperCase().startsWith("INSERT INTO GEOSTORE.")));
    }
    
    @Test
    public void quotesInJsonAreCorrectlyEncoded() throws IOException {
        String sql = "INSERT INTO GEOSTORE.GS_STORED_DATA(ID, STORED_DATA, RESOURCE_ID) VALUES\r\n" + 
                "(74, STRINGDECODE('{\\\"html\\\":\\\"<h1 style=\\\\\\\"text-align:center;\\\\\\\">TEXT</h1>\\\"}'), 74);\r\n";
        
        String normalized = exporter.normalizeInsert(sql); 
        
        String expected = "INSERT INTO GEOSTORE.GS_STORED_DATA(ID, STORED_DATA, RESOURCE_ID) VALUES\r\n" + 
                "(74, '{\"html\":\"<h1 style=\\\"text-align:center;\\\">TEXT</h1>\"}', 74);\r\n";
        assertEquals(expected, normalized);
    }
    
    @Test
    public void stringDecodeIsNotNeededForPgSQL() throws IOException {
        String sql = "INSERT INTO GEOSTORE.GS_STORED_DATA(ID, STORED_DATA, RESOURCE_ID) VALUES\r\n" + 
                "(36, STRINGDECODE('{\\\"widgets\\\":[]}'), 36);\r\n";
        
        String normalized = exporter.normalizeInsert(sql); 
        
        String expected = "INSERT INTO GEOSTORE.GS_STORED_DATA(ID, STORED_DATA, RESOURCE_ID) VALUES\r\n" + 
                "(36, '{\"widgets\":[]}', 36);\r\n";
        assertEquals(expected, normalized);
    }
    
    @Test
    public void hibernateSequenceIsSetToMaxId() throws IOException {
        String script = getValidScript();
        
        List<String> sql = exporter.filterInserts(script);
        int sequence = exporter.getLastUsedId(sql);
        
        assertEquals(58, sequence);
    }
    
    @Test
    public void hibernateSequenceIsSetToZeroForInvalidScript() throws IOException {
        String script = getInvalidScript();
        
        List<String> sql = exporter.filterInserts(script);
        int sequence = exporter.getLastUsedId(sql);
        
        assertEquals(0, sequence);
    }
    
    @Test
    public void exportedSqlIsValid() throws IOException {
        exporter.inputPath = getTestDb();
        
        List<String> sql = split(exporter.extractAndScript());
        
        sql.forEach(s -> assertValidSql(s));
    }
    
    private List<String> split(Optional<String> sql) {
        assertTrue(sql.isPresent());
        return Arrays.asList(sql.get().split("(;\r\n)|(;\n)")).stream().map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private void assertValidSql(String sql) {
        assertTrue(sql.toUpperCase().startsWith("INSERT INTO GEOSTORE.") || sql.toUpperCase().startsWith("ALTER SEQUENCE "));
    }
    
    private void assertContains(String sql, String tableName) {
        assertTrue(sql.toUpperCase().contains(tableName.toUpperCase()));
    }

    private String getValidScript() throws IOException {
        exporter.inputPath = getTestDb();
        return exporter.exportH2AsScript().get();
    }
    
    private String getInvalidScript() throws IOException {
        return "CREATE TABLE TEST\nALTER TABLE TEST\n";
    }
}
