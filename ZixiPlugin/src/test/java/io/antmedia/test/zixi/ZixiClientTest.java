package io.antmedia.test.zixi;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.antmedia.plugin.ZixiPlugin;

public class ZixiClientTest {

    @Test
    public void testInit(){
        ZixiPlugin client = new ZixiPlugin();
        assertTrue(client.init());

        assertTrue(client.start());
    }

}
