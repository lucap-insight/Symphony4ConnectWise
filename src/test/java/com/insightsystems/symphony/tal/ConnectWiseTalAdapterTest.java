package com.insightsystems.symphony.tal;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;
import com.avispl.symphony.api.tal.dto.TalTicket;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import com.avispl.symphony.api.tal.error.TalNotRecoverableException;
import com.avispl.symphony.api.tal.error.TalRecoverableException;

class ConnectWiseTalAdapterTest {
	private static TalAdapter talAdapter;
	private static TalConfigService talConfigService;
	private static TalProxy talRoutingService;
	private static TicketSystemConfig config;
	private static TicketServiceImpl ticketService;
	private static ConnectWiseClient restCWClient;
	private static final String TAL_TICKET = "src/test/resources/talTicketSample1.json";
	private static final String TAL_TICKET_TO_CLOSE = "src/test/resources/talTicketToCW.json";
	private static TalAdapterSyncException recoverableException;
	private static TalAdapterSyncException notRecoverableException;

	@BeforeAll
	public static void init() {
		talConfigService = mock();
		config = mock();
		ticketService = mock(TicketServiceImpl.class);
		talAdapter = new ConnectWiseTalAdapter(talConfigService, ticketService);
		restCWClient = mock();
//		ReflectionTestUtils.setField(talAdapter, "config", config); obviously wrong
		ReflectionTestUtils.setField(talAdapter, "ticketService", ticketService);
//		ReflectionTestUtils.setField(talAdapter, "restCWClient", restCWClient); also wrong

		try {
			when(talConfigService.retrieveTicketSystemConfig(any())).thenReturn(config);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}

		recoverableException = new TalAdapterSyncException("Recoverable exception", HttpStatus.valueOf(408));
		notRecoverableException = new TalAdapterSyncException("Not recoverable exception");
	}

	@AfterEach
	public void resetMocks() {
		// Reset the behavior of classB after each test
		reset(ticketService);
		reset(config);
		reset(talConfigService);
		try {
			when(talConfigService.retrieveTicketSystemConfig(any())).thenReturn(config);
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void syncTalTicket_withNewTicket_shouldCallCreateTicket() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(null);

		// Verify output
		assertEquals(ticket, talAdapter.syncTalTicket(ticket));

		// Ensure that it ran createTicket
		verify(ticketService, times(1)).createTicket(any(TicketSystemConfig.class),any(ConnectWiseTicket.class));
	}

	@Test
	void syncTalTicket_withExistingTicket_shouldCallUpdateTicket() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		// Verify output
		assertEquals(ticket, talAdapter.syncTalTicket(ticket));

		// Ensure that it ran createTicket
		verify(ticketService, times(1)).updateTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class), any(ConnectWiseTicket.class));
	}

	@Test
	void syncTalTicket_whenConfigsAreNotConfigured_shouldThrowNotRecoverableException() throws IOException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(TicketSourceConfigPropertyCW.CLIENT_ID, "someClientId");
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);

//		assertions about result
		assertThrows(TalNotRecoverableException.class, () -> talAdapter.syncTalTicket(ticket),
				"ConnectWise API Credentials missing should raise TalNotRecoverableException.");

	}

	@Test
	void syncTalTicket_whenGetTicketThrowsANotRecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		// What happens when ticketService returns an error
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenThrow(notRecoverableException);

		assertThrows(TalNotRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenGetTicketThrowsARecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		// What happens when ticketService returns an error
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenThrow(recoverableException);

		assertThrows(TalRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenCreateTicketThrowsARecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(null);

		doThrow(recoverableException).when(ticketService).createTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class));

		assertThrows(TalRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenCreateTicketThrowsANotRecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(null);

		doThrow(notRecoverableException).when(ticketService).createTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class));

		assertThrows(TalNotRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenUpdateTicketThrowsARecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		doThrow(recoverableException).when(ticketService)
				.updateTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class), any(ConnectWiseTicket.class));

		assertThrows(TalRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenUpdateTicketThrowsANotRecoverableException_shouldThrow() throws IOException, TalAdapterSyncException {
//		preparing data
		TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
		Map<String, String> mapOfConfigs = Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
		);
		when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);
		when(ticketService.getCWTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		doThrow(notRecoverableException).when(ticketService)
				.updateTicket(any(TicketSystemConfig.class), any(ConnectWiseTicket.class),any(ConnectWiseTicket.class));

		assertThrows(TalNotRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	@Test
	void syncTalTicket_whenTicketNotFound_shouldReturnClosed() throws IOException, ExecutionException, InterruptedException {
		TalTicket talTicket = makeTalTicketFromJson(TAL_TICKET_TO_CLOSE);
		TicketSystemConfig config = new TicketSystemConfig();
		config.setTicketSourceConfig(Map.of(
				TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
				TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
				TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
				TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId",
				TicketSourceConfigProperty.URL,"https://connect.insightsystems.com.au",
				TicketSourceConfigProperty.API_PATH,"/v4_6_release/apis/3.0",
				TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET,"/service/tickets",
				TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS,"/notes",
				TicketSourceConfigPropertyCW.COMPANY_REC_ID,"250"
				)
		);
		when(talConfigService.retrieveTicketSystemConfig(any())).thenReturn(config);
		HttpClient client = mock(HttpClient.class);
		ConnectWiseClient connectWiseClient = new ConnectWiseClient();
		ReflectionTestUtils.setField(connectWiseClient, "client", client);
		TicketServiceImpl ticketService = new TicketServiceImpl(connectWiseClient);
		TalAdapter newTalAdapter = new ConnectWiseTalAdapter(talConfigService, ticketService);
		when(client.send(any(),any())).thenReturn(buildResponse());

		TalTicket result = newTalAdapter.syncTalTicket(talTicket);

		assertEquals("Closed", result.getStatus());
	}

	private static HttpResponse buildResponse() {
		return new HttpResponse() {
			@Override
			public int statusCode() {
				return 404;
			}

			@Override
			public HttpRequest request() {
				return null;
			}

			@Override
			public Optional<HttpResponse> previousResponse() {
				return Optional.empty();
			}

			@Override
			public HttpHeaders headers() {
				return null;
			}

			@Override
			public Object body() {
				return null;
			}

			@Override
			public Optional<SSLSession> sslSession() {
				return Optional.empty();
			}

			@Override
			public URI uri() {
				return null;
			}

			@Override
			public Version version() {
				return null;
			}
		};
	}

	private static TalTicket makeTalTicketFromJson(String path) throws IOException {
		String talTicketJson = Files.readString(Path.of(path));
		// Had to add fail on unknown properties because some properties are not on my current version of the TAL adapter
		return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(talTicketJson, TalTicket.class);
	}
}
