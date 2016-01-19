/*
 *  Copyright (C) 2007 - 2014 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 * 
 *  GPLv3 + Classpath exception
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.geosolutions.geostore.core.security.password;

import it.geosolutions.geostore.core.security.password.PwEncoder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test for password encoder
 * @author ETj <etj at geo-solutions.it>
 * @author Lorenzo Natali <lorenzo.natali at geo-solutions.it>
 */
public class PwEncoderTest {

    public PwEncoderTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Test
    public void testEncode() {
        testString("test");
        testString("topolino");
        testString("");

    }
    /**
     * Test encode and decode string
     * @param test
     */
    public void testString(String test) {
        String enc = PwEncoder.encode(test);
        System.out.println("ENC --> " + enc);
        if( PwEncoder.getEncoder().isReversible() ){
    		String dec = PwEncoder.decode(enc);
    		System.out.println("DEC --> " + dec);
            assertEquals(test, dec);
        }
        assertTrue(PwEncoder.isPasswordValid(enc, test));
    }

}