package net.ripe.rpki.ui.commons;

import org.apache.wicket.markup.html.form.upload.FileUpload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

public final class FileUploadUtils {

    private static final int BUFFER_SIZE = 1024;

    private FileUploadUtils() {
        // utility classes should not have a public constructor
    }

    public static String convertUploadedFileToString(FileUpload fileUpload) throws IOException {
        InputStream is = fileUpload.getInputStream();
        try {

            StringWriter sw = new StringWriter();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            char[] buffer = new char[BUFFER_SIZE];

            int n;
            while ((n = reader.read(buffer)) != -1) {
                sw.write(buffer, 0, n);
            }

            return sw.toString();
        } finally {
            is.close();
        }
    }
}
