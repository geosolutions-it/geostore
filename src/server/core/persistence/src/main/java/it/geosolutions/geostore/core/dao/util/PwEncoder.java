/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.core.dao.util;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

/**
 * 
 * @author ETj <etj at geo-solutions.it>
 */
public class PwEncoder {

    // 123456789 123456789 123456789 12
    private static final byte[] KEY = "installation dependant key needed".substring(0, 16)
            .getBytes();

    public static String encode(String msg) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] input = msg.getBytes();
            byte[] encrypted = cipher.doFinal(input);
            byte[] output = Base64.encodeBase64(encrypted);
            return new String(output);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (NoSuchPaddingException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (IllegalBlockSizeException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (BadPaddingException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (InvalidKeyException ex) {
            throw new RuntimeException("Error while encoding", ex);
        }
    }

    public static String decode(String msg) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] de64 = Base64.decodeBase64(msg);
            byte[] decrypted = cipher.doFinal(de64);

            return new String(decrypted);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (NoSuchPaddingException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (IllegalBlockSizeException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (BadPaddingException ex) {
            throw new RuntimeException("Error while encoding", ex);
        } catch (InvalidKeyException ex) {
            throw new RuntimeException("Error while encoding", ex);
        }
    }
}
