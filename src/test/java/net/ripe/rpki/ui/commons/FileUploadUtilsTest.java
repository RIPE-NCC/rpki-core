package net.ripe.rpki.ui.commons;

import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class FileUploadUtilsTest {

    @Test
    public void shouldConvertUploadedFileToString() throws IOException {
        FileUpload fileUpload = mock(FileUpload.class);

        String expectedString = "this is a test string";


        InputStream mockedIs = new ByteArrayInputStream(expectedString.getBytes());
        when(fileUpload.getInputStream()).thenReturn(mockedIs);

        String extractedString = FileUploadUtils.convertUploadedFileToString(fileUpload);

        assertEquals(expectedString, extractedString);
    }

}
