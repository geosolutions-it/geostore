/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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

import org.apache.commons.codec.binary.Base64;
import org.jasypt.digest.StandardByteDigester;
import org.jasypt.spring.security.PasswordEncoder;
import org.jasypt.util.password.StrongPasswordEncryptor;
import static it.geosolutions.geostore.core.security.password.SecurityUtils.toBytes;

/**
 * This Encoder provide encription and check of password using a digest
 * @author Lorenzo Natali (lorenzo.natali at geo-solutions.it)
 *
 */
public class GeoStoreDigestPasswordEncoder extends AbstractGeoStorePasswordEncoder{
	

		/**
		 * The digest is not reversible
		 */
	    public GeoStoreDigestPasswordEncoder() {
	        setReversible(false);
	    }

	    @Override
	    protected PasswordEncoder createStringEncoder() {
	        PasswordEncoder encoder = new PasswordEncoder();
	        encoder.setPasswordEncryptor(new StrongPasswordEncryptor());
	        return encoder;
	    }

	    @Override
	    protected CharArrayPasswordEncoder createCharEncoder() {
	        return new CharArrayPasswordEncoder() {
	            StandardByteDigester digester = new StandardByteDigester();
	            {
	                digester.setAlgorithm("SHA-256");
	                digester.setIterations(100000);
	                digester.setSaltSizeBytes(16);
	                digester.initialize();
	            }
	            
	            @Override
	            public String encodePassword(char[] rawPass, Object salt) {
	                return new String(Base64.encodeBase64(digester.digest(toBytes(rawPass))));
	            }
	            @Override
	            public boolean isPasswordValid(String encPass, char[] rawPass, Object salt) {
	                return digester.matches(toBytes(rawPass), Base64.decodeBase64(encPass.getBytes())); 
	            }
	        };
	    }

	    @Override
	    public PasswordEncodingType getEncodingType() {
	        return PasswordEncodingType.DIGEST;
	    }
	
}
