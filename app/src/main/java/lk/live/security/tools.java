package lk.live.security;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.Random;

public class tools {
    private static String exec(String command) throws Exception {
        Process exec = Runtime.getRuntime().exec(command);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exec.getInputStream()));
        BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(exec.getErrorStream()));
        String str2 = "";
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                break;
            }
            str2 = new StringBuffer().append(new StringBuffer().append(str2).append(readLine).toString()).append('\n').toString();
        }
        String str3 = "";
        while (true) {
            String readLine2 = bufferedReader2.readLine();
            if (readLine2 == null) {
                break;
            }
            str3 = new StringBuffer().append(new StringBuffer().append(str3).append(readLine2).toString()).append('\n').toString();
        }
        if (str2.trim().length() <= 0) {
            return "";
        }
        return str2;
    }

    private static String getFileChecksum(String str, String str2) throws Exception {
        int i;
        MessageDigest messageDigest = MessageDigest.getInstance(str);
        FileInputStream fileInputStream = new FileInputStream(new File(str2));
        byte[] bArr = new byte[1024];
        while (true) {
            int read = fileInputStream.read(bArr);
            if (read == -1) {
                break;
            }
            messageDigest.update(bArr, 0, read);
        }
        fileInputStream.close();
        byte[] digest = messageDigest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Integer.toString((b & 255) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    public static String getApkChecksum(Context context, String str) throws Exception {
        String absolutePath;
        String str2 = "";
        for (String str3 : exec(new StringBuffer().append("pm path ").append(str).toString()).split("\n")) {
            if (str3.trim().startsWith("package:")) {
                str2 = str3.split(":")[1].trim();
            }
        }
        if (context.getExternalFilesDirs(null) != null) {
            absolutePath = context.getExternalFilesDirs(null)[0].getAbsolutePath();
        } else {
            absolutePath = context.getFilesDir().getAbsolutePath();
        }
        String stringBuffer = new StringBuffer().append(new StringBuffer().append(new StringBuffer().append(absolutePath).append("/").toString()).append(new Random().nextInt(9999)).toString()).append(".bin").toString();
        exec(new StringBuffer().append(new StringBuffer().append(new StringBuffer().append("cp ").append(str2).toString()).append(" ").toString()).append(stringBuffer).toString());
        String fileChecksum = getFileChecksum("SHA-256", stringBuffer);
        new File(stringBuffer).delete();
        return fileChecksum;
    }
}



