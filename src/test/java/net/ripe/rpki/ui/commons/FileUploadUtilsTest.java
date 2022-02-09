package net.ripe.rpki.ui.commons;

import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;


public class FileUploadUtilsTest {

    @Test
    public void shouldConvertUploadedFileToString() throws IOException {
        FileUpload fileUpload = createMock(FileUpload.class);

        String expectedString = "this is a test string";


        InputStream mockedIs = new ByteArrayInputStream(expectedString.getBytes());
        expect(fileUpload.getInputStream()).andReturn(mockedIs);

        replay(fileUpload);
        String extractedString = FileUploadUtils.convertUploadedFileToString(fileUpload);
        verify(fileUpload);

        assertEquals(expectedString, extractedString);
    }

}
