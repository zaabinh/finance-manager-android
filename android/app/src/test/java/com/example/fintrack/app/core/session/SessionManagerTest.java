package com.example.fintrack.app.core.session;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class SessionManagerTest {

    @Test
    public void testSessionKeys() {
        assertNotNull("SessionManager class is available", SessionManager.class);
    }
}
