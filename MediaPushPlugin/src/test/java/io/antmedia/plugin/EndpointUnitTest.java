package io.antmedia.plugin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.antmedia.model.Endpoint;

public class EndpointUnitTest {

    @Test
    public void testExtraChromeSwitches() {
        Endpoint endpoint = new Endpoint();
        
        assertNull(endpoint.getExtraChromeSwitches());
        endpoint.setExtraChromeSwitches("switches");
        
        assertEquals("switches", endpoint.getExtraChromeSwitches());
      
    }
}