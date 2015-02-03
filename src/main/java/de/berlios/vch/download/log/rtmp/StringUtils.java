/* 
 * Copyright (c) Henrik Niehaus
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its 
 *    contributors may be used to endorse or promote products derived from this 
 *    software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package de.berlios.vch.download.log.rtmp;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class StringUtils {

    public static final int LOG_BYTE_DUMP_SIZE = 8 * 4;

    /**
     * Creates a hexdump with a maximum length of {@link StringUtils#LOG_BYTE_DUMP_SIZE} bytes
     * 
     * @param data
     * @return hexdump as String
     */
    public static String toHeadHexString(byte[] data) {
        int length = Math.min(data.length, LOG_BYTE_DUMP_SIZE);
        byte[] head = new byte[length];
        System.arraycopy(data, 0, head, 0, length);

        StringBuilder sb = new StringBuilder(toHexString(head));
        sb.append("\n  ...");
        return sb.toString();
    }

    public static String toHexString(byte[] bytes) {
        return toHexString(bytes, 8);
    }

    public static String toHexString(byte[] bytes, int bytesPerRow) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += bytesPerRow) {
            int length = bytes.length - i >= bytesPerRow ? bytesPerRow : bytes.length % bytesPerRow;
            byte[] row = new byte[bytesPerRow];
            System.arraycopy(bytes, i, row, 0, length);

            for (int j = 0; j < length; j++) {
                sb.append('0');
                sb.append('x');
                sb.append(toHexString(row[j]));
                sb.append(' ');
            }

            for (int j = 0; j < bytesPerRow - length; j++) {
                sb.append("     ");
            }

            for (int j = 0; j < Math.min(length, bytesPerRow); j++) {
                if (row[j] >= 32 && row[j] <= 127) {
                    sb.append((char) row[j]);
                } else {
                    sb.append('.');
                }
            }

            if (i + bytesPerRow < bytes.length) {
                sb.append('\n');
                sb.append(' ');
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Converts one byte to its hex representation with leading zeros. E.g. 255 -> FF, 12 -> 0C
     * 
     * @param b
     * @return
     */
    public static String toHexString(int b) {
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    public static String toCharString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            char c = (char) (bytes[i] & 0xFF);
            sb.append(c);
            sb.append(' ');
            if (i % 8 == 7) {
                sb.append('\n');
                sb.append(' ');
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    public static CharsetEncoder getEncoder(String charset) {
        return Charset.forName(charset).newEncoder();
    }

    public static CharsetDecoder getDecoder(String charset) {
        return Charset.forName(charset).newDecoder();
    }

    // public static void main(String[] args) {
    // byte[] bytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    // System.out.println(toHexString(bytes));
    // }
}
