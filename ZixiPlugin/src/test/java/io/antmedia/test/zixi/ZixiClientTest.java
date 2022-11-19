package io.antmedia.test.zixi;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.antmedia.plugin.ZixiClient;

public class ZixiClientTest {

    @Test
    public void testInit(){
        ZixiClient client = new ZixiClient();
        assertTrue(client.init());

        assertTrue(client.start());
    }

}
