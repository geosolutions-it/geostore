/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.geosolutions.geostore.core.dao.util;

import it.geosolutions.geostore.core.dao.util.PwEncoder;
import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author ETj <etj at geo-solutions.it>
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

    public void testString(String test) {

        String enc = PwEncoder.encode(test);
        System.out.println("ENC --> " + enc);
        String dec = PwEncoder.decode(enc);
        System.out.println("DEC --> " + dec);
        assertEquals(test, dec);
    }

}