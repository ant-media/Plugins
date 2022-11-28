package io.antmedia.test.zixi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.antmedia.zixi.ZixiFeeder;
import io.vertx.core.Vertx;

public class ZixiFeederTest {
    
    Vertx vertx = Vertx.vertx();

    @Test
    public void testLoggingConfigure() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("url", vertx);

        assertTrue(zixiFeeder.configureLogLevel(3));
    }

    @Test
    public void testParseURL() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("zixi://127.0.12.12:2066/test678", vertx);

        assertEquals("127.0.12.12", zixiFeeder.getHost());
        assertEquals(2066, zixiFeeder.getPort());
        assertEquals("test678", zixiFeeder.getChannel());
    }

    @Test
    public void testConnect() {
        //this test requires ZixiBroacaster running on 127.0.0.1 
        // and "stream1" should be added as push type in the Inputs
        ZixiFeeder zixiFeeder = new ZixiFeeder("zixi://127.0.0.1:2088/stream1", vertx);

        assertEquals("127.0.0.1", zixiFeeder.getHost());
        assertEquals(2088, zixiFeeder.getPort());
        assertEquals("stream1", zixiFeeder.getChannel());

        assertTrue(zixiFeeder.connect().isSuccess());

        assertTrue(zixiFeeder.disconnect());
    }


    @Test
    public void testConnectFail() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("zisssxi://127.0.0.1:2088/stream1", vertx);

        assertFalse(zixiFeeder.connect().isSuccess());

        assertFalse(zixiFeeder.disconnect());
    }
}
