package reportserver;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

class FileHandler {
    private String currentDir;

    FileHandler(String currentDir_) {
        currentDir = currentDir_;
    }

    public byte[] getMD5ForFile(String file)
            throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = getFileDigest(new FileInputStream(file), md);
        return digest;
    }

    private byte[] getFileDigest(InputStream input_stream, MessageDigest md)
            throws NoSuchAlgorithmException, IOException {
        md.reset();
        byte[] bytes = new byte[10];
        int numBytes;

        while ((numBytes = input_stream.read(bytes)) != -1) {
            md.update(bytes, 0, numBytes);
        }

        byte[] digest = md.digest();

        return digest;
    }

    public String generateName(String uniqPart, String ext) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        String finishDate = dateFormat.format(new Date());
        String name = uniqPart+"-"+finishDate+"."+ext;
        File newFile = new File(name);

        for (int i = 0; (i < Integer.MAX_VALUE) && newFile.exists(); ++i) {
            name = uniqPart+finishDate+"("+i+")."+ext;
            newFile = new File(name);
        }

        return name;
    }


}
