package com.insightsystems.symphony.tal;


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;
import com.avispl.symphony.api.tal.dto.TalTicket;
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
	private static TalAdapterSyncException recoverableException;
	private static TalAdapterSyncException notRecoverableException;

	@BeforeAll
	public static void init() {
		talRoutingService = mock();
		talConfigService = mock();
		talAdapter = new ConnectWiseTalAdapter(talConfigService, talRoutingService);

		config = mock();
		ticketService = mock(TicketServiceImpl.class);
		restCWClient = mock();
		ReflectionTestUtils.setField(talAdapter, "config", config);
		ReflectionTestUtils.setField(talAdapter, "ticketService", ticketService);
		ReflectionTestUtils.setField(talAdapter, "restCWClient", restCWClient);

		recoverableException = new TalAdapterSyncException("Recoverable exception", HttpStatus.valueOf(408));
		notRecoverableException = new TalAdapterSyncException("Not recoverable exception");
	}

	@AfterEach
	public void resetMocks() {
		// Reset the behavior of classB after each test
		reset(ticketService);
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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(null);

		// Verify output
		Assertions.assertEquals(ticket, talAdapter.syncTalTicket(ticket));

		// Ensure that it ran createTicket
		verify(ticketService, times(1)).createTicket(any(ConnectWiseTicket.class));
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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		// Verify output
		Assertions.assertEquals(ticket, talAdapter.syncTalTicket(ticket));

		// Ensure that it ran createTicket
		verify(ticketService, times(1)).updateTicket(any(ConnectWiseTicket.class), any(ConnectWiseTicket.class));
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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenThrow(notRecoverableException);

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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenThrow(recoverableException);

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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(null);

		doThrow(recoverableException).when(ticketService).createTicket(any(ConnectWiseTicket.class));

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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(null);

		doThrow(notRecoverableException).when(ticketService).createTicket(any(ConnectWiseTicket.class));

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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		doThrow(recoverableException).when(ticketService)
				.updateTicket(any(ConnectWiseTicket.class), any(ConnectWiseTicket.class));

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
		when(ticketService.getCWTicket(any(ConnectWiseTicket.class))).thenReturn(mock(ConnectWiseTicket.class));

		doThrow(notRecoverableException).when(ticketService)
				.updateTicket(any(ConnectWiseTicket.class),any(ConnectWiseTicket.class));

		assertThrows(TalNotRecoverableException.class, () -> talAdapter.syncTalTicket(ticket));
	}

	private static TalTicket makeTalTicketFromJson(String path) throws IOException {
		String talTicketJson = Files.readString(Path.of(path));
		// Had to add fail on unknown properties because some properties are not on my current version of the TAL adapter
		return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(talTicketJson, TalTicket.class);
	}
}
