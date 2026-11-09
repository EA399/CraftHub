package de.crafthub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrosoftAuthenticatorTest {
    @Test
    void normalizesMicrosoftApplicationId() {
        assertEquals("c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
                MicrosoftAuthenticator.normalizeClientId(" C36A9FB6-4F2A-41FF-90BD-AE7CC92031EB "));
    }

    @Test
    void rejectsInvalidMicrosoftApplicationId() {
        assertThrows(IllegalArgumentException.class,
                () -> MicrosoftAuthenticator.normalizeClientId("not-an-application-id"));
        assertThrows(IllegalArgumentException.class,
                () -> MicrosoftAuthenticator.normalizeClientId(" "));
    }
}