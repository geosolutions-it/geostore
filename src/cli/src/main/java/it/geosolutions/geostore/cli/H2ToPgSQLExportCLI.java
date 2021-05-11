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
import picocli.CommandLine;

/**
 * CLI tool to export a GeoStore H2 database as a script for a destination database.
 * Usage: H2ToPgSQL [-o, --output=<path to output sql>] <path to H2 database file>
 */
public class H2ToPgSQLExportCLI {
    
    public static void main(String[] args) {
        H2ToPgSQLExporter exporter = new H2ToPgSQLExporter();
        System.exit(new CommandLine(exporter).execute(args));
    }
    
}
