package de.berlios.vch.download.rtmp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.zip.InflaterInputStream;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SwfFile {
    
    /**
     * Reads a SWF file from the InputStream, decompresses it and writes the resulting file to the OutputStream.
     * The streams don't get closed.
     * @param in
     * @param out
     * @throws IOException
     */
    public static void decompressSwf(InputStream in, OutputStream out) throws IOException {
        boolean compressed = true;
        int compressIndicator = in.read(); // C or F
        if(compressIndicator == 'C') {
            compressed = true;
        } else {
            compressed = false;
        }
        
        in.skip(2); // skip W and S
        
        // write uncompressed signature
        out.write('F');
        out.write('W');
        out.write('S');
        
        // write swf version
        out.write(in.read());
        
        // write file size
        out.write(in.read());
        out.write(in.read());
        out.write(in.read());
        out.write(in.read());
        
        if(compressed) {
            in = new InflaterInputStream(in);
        }
        
        byte[] b = new byte[1024];
        int length = -1;
        while( (length = in.read(b)) >= 0 ) {
            out.write(b, 0, length);
        }
    }
    
    /**
     * Calculates a SHA256 HMAC of the given data with the given key
     * @param key
     * @param data
     * @return a String representation of the sha256 hmac
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static String hmacSha256(String key, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKey _key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        hmac.init(_key);
        hmac.update(data);
        byte[] digest = hmac.doFinal();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            String s = Integer.toHexString(digest[i] & 0xFF);
            s = s.length() == 1 ? "0" + s : s;
            sb.append(s.toUpperCase());
        }
        return sb.toString();
    }
    
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        File outFile = new File("/tmp/player_decompressed.swf");
        InputStream in = new FileInputStream("/tmp/player.swf");
        OutputStream out = new FileOutputStream(outFile);
        SwfFile.decompressSwf(in, out);
        in.close();
        out.close();
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(outFile);
        byte[] b = new byte[1024];
        int length = -1;
        while( (length = fin.read(b)) >= 0 ) {
            bos.write(b, 0, length);
        }
        
        System.out.println("Digest: " + hmacSha256("Genuine Adobe Flash Player 001", bos.toByteArray()));
    }
}
