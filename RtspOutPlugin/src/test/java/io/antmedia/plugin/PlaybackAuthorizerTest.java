package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Subscriber;
import io.antmedia.datastore.db.types.Token;
import io.antmedia.security.ITokenService;

public class PlaybackAuthorizerTest {

	private static final String STREAM_ID = "stream1";
	private static final String PATH = "LiveApp/" + STREAM_ID;

	private PlaybackAuthorizer authorizer;
	private RtspOutPlugin plugin;
	private AppSettings appSettings;
	private DataStore dataStore;
	private ITokenService tokenService;

	@Before
	public void setUp() {
		plugin = mock(RtspOutPlugin.class);
		appSettings = mock(AppSettings.class);
		dataStore = mock(DataStore.class);
		tokenService = mock(ITokenService.class);

		when(plugin.getAppSettings()).thenReturn(appSettings);
		when(plugin.getDataStore()).thenReturn(dataStore);
		when(plugin.getTokenService()).thenReturn(tokenService);
		when(plugin.streamIdOf(PATH)).thenReturn(STREAM_ID);

		authorizer = new PlaybackAuthorizer(plugin);
	}

	private RtspAuthRequest request(String action, String ip, String query) {
		RtspAuthRequest request = new RtspAuthRequest();
		request.setAction(action);
		request.setIp(ip);
		request.setPath(PATH);
		request.setQuery(query);
		request.setId("connection-1");
		return request;
	}

	@Test
	public void testOnlyTheServerItselfCanPublish() {
		assertTrue(authorizer.authorize(request("publish", "127.0.0.1", null)).isSuccess());
		assertFalse(authorizer.authorize(request("publish", "10.0.0.9", null)).isSuccess());
	}

	@Test
	public void testUnknownActionIsRefused() {
		assertFalse(authorizer.authorize(request("api", "127.0.0.1", null)).isSuccess());
	}

	@Test
	public void testReadIsOpenWhenNoSecurityIsEnabled() {
		assertTrue(authorizer.authorize(request("read", "10.0.0.9", null)).isSuccess());
	}

	@Test
	public void testPlayTokenFromQuery() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		when(tokenService.checkToken("good", STREAM_ID, "10.0.0.9|" + STREAM_ID + "|good||", Token.PLAY_TOKEN)).thenReturn(true);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "token=good")).isSuccess());
		assertFalse(authorizer.authorize(request("read", "10.0.0.9", "token=bad")).isSuccess());
	}

	/** MediaMTX mirrors the RTSP password into the token field, so both spellings have to work. */
	@Test
	public void testPlayTokenFallsBackToTheRtspPassword() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		when(tokenService.checkToken("good", STREAM_ID, "10.0.0.9|" + STREAM_ID + "|good||", Token.PLAY_TOKEN)).thenReturn(true);

		RtspAuthRequest mirrored = request("read", "10.0.0.9", null);
		mirrored.setPassword("good");
		mirrored.setToken("good");
		assertTrue(authorizer.authorize(mirrored).isSuccess());

		RtspAuthRequest passwordOnly = request("read", "10.0.0.9", null);
		passwordOnly.setPassword("good");
		assertTrue(authorizer.authorize(passwordOnly).isSuccess());
	}

	@Test
	public void testTimeBasedSubscriber() {
		when(appSettings.isEnableTimeTokenForPlay()).thenReturn(true);
		when(tokenService.checkTimeBasedSubscriber("sub1", STREAM_ID, "10.0.0.9|" + STREAM_ID + "||sub1|123456", "123456",
				Subscriber.PLAY_TYPE)).thenReturn(true);

		assertTrue(authorizer.authorize(
				request("read", "10.0.0.9", "subscriberId=sub1&subscriberCode=123456")).isSuccess());
		assertFalse(authorizer.authorize(
				request("read", "10.0.0.9", "subscriberId=sub1&subscriberCode=000000")).isSuccess());
	}

	@Test
	public void testSecurityWithoutATokenServiceRefuses() {
		RtspOutPlugin plugin = mock(RtspOutPlugin.class);
		when(plugin.getAppSettings()).thenReturn(appSettings);
		when(plugin.streamIdOf(PATH)).thenReturn(STREAM_ID);
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);

		assertFalse(new PlaybackAuthorizer(plugin)
				.authorize(request("read", "10.0.0.9", "token=good")).isSuccess());
	}

	@Test
	public void testBlockedSubscriberIsRefusedWithNoOtherSecurity() {
		Subscriber subscriber = new Subscriber();
		subscriber.setBlockedType(Subscriber.PLAY_TYPE);
		subscriber.setBlockedUntilUnitTimeStampMs(System.currentTimeMillis() + 60000);

		when(dataStore.getSubscriber(STREAM_ID, "sub1")).thenReturn(subscriber);

		assertFalse(authorizer.authorize(request("read", "10.0.0.9", "subscriberId=sub1")).isSuccess());
		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "subscriberId=other")).isSuccess());
	}

	@Test
	public void testUnknownPathIsRefused() {
		RtspAuthRequest request = request("read", "10.0.0.9", null);
		request.setPath("NoSuchApp/stream1");

		assertFalse(RtspOutPlugin.authorize(request).isSuccess());
	}

	@Test
	public void testQueryValuesAreUrlDecoded() {
		when(appSettings.isPlayJwtControlEnabled()).thenReturn(true);
		when(tokenService.checkJwtToken(eq("a b+c"), any(), any(), any())).thenReturn(true);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "token=a%20b%2Bc")).isSuccess());
	}

	@Test
	public void testSessionFollowsTheCredentialNotTheConnectionId() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		ArgumentCaptor<String> sessions = ArgumentCaptor.forClass(String.class);
		when(tokenService.checkToken(eq("good"), eq(STREAM_ID), sessions.capture(), eq(Token.PLAY_TOKEN)))
				.thenReturn(true);

		// MediaMTX authorizes one read twice, under a different id each time. Both must land on one session
		RtspAuthRequest describe = request("read", "10.0.0.9", "token=good");
		describe.setId("connection-uuid");
		RtspAuthRequest setup = request("read", "10.0.0.9", "token=good");
		setup.setId("session-uuid");

		assertTrue(authorizer.authorize(describe).isSuccess());
		assertTrue(authorizer.authorize(setup).isSuccess());
		assertEquals(sessions.getAllValues().get(0), sessions.getAllValues().get(1));

		// a different credential must not ride that session
		when(tokenService.checkToken(eq("other"), any(), any(), any())).thenReturn(false);
		assertFalse(authorizer.authorize(request("read", "10.0.0.9", "token=other")).isSuccess());
	}

	/** A refused viewer with a vague reason is unusable in support, so every check names itself. */
	@Test
	public void testEachCheckRefusesWithItsOwnReason() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		assertEquals("the token is invalid",
				authorizer.authorize(request("read", "10.0.0.9", "token=bad")).getMessage());
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(false);

		when(appSettings.isHashControlPlayEnabled()).thenReturn(true);
		assertEquals("the hash is invalid",
				authorizer.authorize(request("read", "10.0.0.9", "token=bad")).getMessage());
		when(appSettings.isHashControlPlayEnabled()).thenReturn(false);

		when(appSettings.isPlayJwtControlEnabled()).thenReturn(true);
		assertEquals("the JWT token is invalid",
				authorizer.authorize(request("read", "10.0.0.9", "token=bad")).getMessage());
		when(appSettings.isPlayJwtControlEnabled()).thenReturn(false);

		when(appSettings.isEnableTimeTokenForPlay()).thenReturn(true);
		assertEquals("the subscriber id or code is invalid",
				authorizer.authorize(request("read", "10.0.0.9", "subscriberId=s&subscriberCode=1")).getMessage());
	}

	@Test
	public void testHashAdmitsTheSameWayAToken() {
		when(appSettings.isHashControlPlayEnabled()).thenReturn(true);
		when(tokenService.checkHash("goodhash", STREAM_ID, "10.0.0.9|" + STREAM_ID + "|goodhash||", Token.PLAY_TOKEN))
				.thenReturn(true);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "token=goodhash")).isSuccess());
		assertFalse(authorizer.authorize(request("read", "10.0.0.9", "token=otherhash")).isSuccess());
	}

	@Test
	public void testRequestsAreRefusedBeforeTheAppIsUp() {
		when(plugin.getAppSettings()).thenReturn(null);

		assertEquals("the application is still getting initialized",
				authorizer.authorize(request("read", "10.0.0.9", null)).getMessage());
	}

	@Test
	public void testUnreadableStreamIdIsRefused() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		when(plugin.streamIdOf(PATH)).thenReturn("");

		assertEquals("the stream id cannot be read from the RTSP path",
				authorizer.authorize(request("read", "10.0.0.9", "token=good")).getMessage());
	}

	@Test
	public void testBlockedCheckIsSkippedWithoutADataStore() {
		when(plugin.getDataStore()).thenReturn(null);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "subscriberId=sub1")).isSuccess());
	}

	/** URLDecoder throws on a truncated escape. The raw value has to reach the token service anyway. */
	@Test
	public void testMalformedPercentEncodingDoesNotEatTheCredential() {
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		when(tokenService.checkToken(eq("100%"), any(), any(), any())).thenReturn(true);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "token=100%")).isSuccess());
	}

	/** Nothing reports an RTSP session ending, so without the sweep the token service maps only grow. */
	@SuppressWarnings("unchecked")
	@Test
	public void testStaleSessionsAreSweptOutOfTheTokenService() throws Exception {
		Map<String, String> authenticated = new HashMap<>();
		when(tokenService.getAuthenticatedMap()).thenReturn(authenticated);
		when(tokenService.getSubscriberAuthenticatedMap()).thenReturn(new HashMap<>());
		when(appSettings.isPlayTokenControlEnabled()).thenReturn(true);
		when(tokenService.checkToken(eq("good"), any(), any(), any())).thenReturn(true);

		assertTrue(authorizer.authorize(request("read", "10.0.0.9", "token=good")).isSuccess());
		Map<String, Long> seen = (Map<String, Long>) field("sessionsSeen");
		assertEquals(1, seen.size());
		authenticated.put(seen.keySet().iterator().next(), "cached by the token service");

		// put the sweep and the session itself well past their windows
		set("lastSweepMs", System.currentTimeMillis() - 120_000);
		seen.replaceAll((session, seenAt) -> System.currentTimeMillis() - 120_000);

		authorizer.authorize(request("read", "10.0.0.9", "token=good"));

		assertTrue("the token service session map must not grow forever", authenticated.isEmpty());
		assertEquals("only the session just admitted survives", 1, seen.size());
	}

	private Object field(String name) throws Exception {
		Field field = PlaybackAuthorizer.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(authorizer);
	}

	private void set(String name, Object value) throws Exception {
		Field field = PlaybackAuthorizer.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(authorizer, value);
	}
}
