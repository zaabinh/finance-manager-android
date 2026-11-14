package com.example.fintrack.app.core.network;
import static org.junit.Assert.*;
import com.example.fintrack.app.core.error.AppError;
import org.junit.Test;
public class ErrorMapperTest {
    @Test public void parserNeedsNoAndroidContext() {
        assertEquals("LOCAL_TIMEOUT", HttpErrorParser.from(new java.net.SocketTimeoutException()).code);
        assertEquals(AppError.Category.NETWORK, HttpErrorParser.from(new java.io.IOException()).category);
    }
}
