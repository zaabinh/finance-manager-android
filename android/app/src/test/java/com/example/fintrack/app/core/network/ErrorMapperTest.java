package com.example.fintrack.app.core.network;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

public class ErrorMapperTest {

    @Test
    public void getErrorMessage_withNullContext_returnsFallbackText() {
        String timeoutError = ErrorMapper.getErrorMessage(null, new SocketTimeoutException("timeout"));
        assertNotNull(timeoutError);

        String connectionError = ErrorMapper.getErrorMessage(null, new IOException("connection failed"));
        assertNotNull(connectionError);
    }

    @Test
    public void getHttpErrorMessage_withNullContext_returnsFallbackText() {
        String httpError = ErrorMapper.getHttpErrorMessage(null, null);
        assertNotNull(httpError);
    }
}
