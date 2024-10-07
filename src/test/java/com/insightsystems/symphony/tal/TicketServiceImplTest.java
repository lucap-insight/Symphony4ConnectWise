package com.insightsystems.symphony.tal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

public class TicketServiceImplTest {

    private static TicketServiceImpl ticketService;
    private static ConnectWiseClient restCWClient;
    private static TicketSystemConfig config;
    private static TalAdapterSyncException recoverableException;
    private static TalAdapterSyncException notRecoverableException;


    @BeforeAll
    public static void init() {
        // setup mock CW Client
        restCWClient = mock(ConnectWiseClient.class);
        config = mock(TicketSystemConfig.class);

        Map<String, String> mapOfConfigs = Map.of(
                TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
                TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
                TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
                TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId",
                TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET, "mockUrlPatternToGetTickets",
                TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS, "mockUrlPatternToGetComments"
        );
        when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);

        recoverableException = new TalAdapterSyncException("Recoverable exception", HttpStatus.valueOf(408));
        notRecoverableException = new TalAdapterSyncException("Not recoverable exception");

        ticketService = new TicketServiceImpl(restCWClient);
    }

    @AfterEach
    public void resetMocks() {
        // Reset the behavior of classB after each test
        reset(restCWClient);
    }


    @Test
    void getTicket_newTicket_shouldReturnTheSameTicket() throws TalAdapterSyncException {
        // Set up mock ticket to get
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        when(CWTicket.getUrl()).thenReturn("url");
        when(CWTicket.getId()).thenReturn("id");
        Map<String, String> extraParams = new HashMap<>();
        when(CWTicket.getExtraParams()).thenReturn(extraParams);
        // Set up mock ticket returned by restCWClient
        ConnectWiseTicket expectedCWTicketReturn = mock(ConnectWiseTicket.class);
        when(restCWClient.get(config,"url")).thenReturn(expectedCWTicketReturn);

        ConnectWiseTicket refreshedCWTicket = ticketService.getCWTicket(config, CWTicket);

        Assertions.assertEquals(expectedCWTicketReturn, refreshedCWTicket);
        Assertions.assertEquals("true", extraParams.get("synced"));
        Assertions.assertEquals("false", extraParams.get("connectionFailed"));
    }

    @Test
    void getTicket_whenCWClientFails_shouldReturnNull() throws TalAdapterSyncException {
        // Set up mock ticket to get
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        when(CWTicket.getUrl()).thenReturn("url");
        when(CWTicket.getId()).thenReturn("id");
        Map<String, String> extraParams = new HashMap<>();
        when(CWTicket.getExtraParams()).thenReturn(extraParams);
        // Set up mock ticket returned by restCWClient
        when(restCWClient.get(config,"url")).thenReturn(null);

        ConnectWiseTicket refreshedCWTicket = ticketService.getCWTicket(config, CWTicket);

        Assertions.assertNull(refreshedCWTicket);
        Assertions.assertEquals("true", extraParams.get("connectionFailed"));
    }

    @Test
    void getTicket_whenCWClientThrowsException_shouldReturnNull() throws TalAdapterSyncException {
        // Set up mock ticket to get
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        when(CWTicket.getUrl()).thenReturn("url");
        when(CWTicket.getId()).thenReturn("id");
        Map<String, String> extraParams = new HashMap<>();
        when(CWTicket.getExtraParams()).thenReturn(extraParams);
        // Set up mock ticket returned by restCWClient
        when(restCWClient.get(config,"url")).thenThrow(new TalAdapterSyncException(""));

        ConnectWiseTicket refreshedCWTicket = ticketService.getCWTicket(config, CWTicket);

        Assertions.assertNull(refreshedCWTicket);
        Assertions.assertEquals("true", extraParams.get("connectionFailed"));
    }

    @Test
    void getTicket_newTicketThatHasFailedBeforeFail_shouldThrowTalAdapterSyncException() throws TalAdapterSyncException {
        // Set up mock ticket to get
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        when(CWTicket.getUrl()).thenReturn("url");
        Map<String, String> extraParams = Map.of("connectionFailed", "true");
        when(CWTicket.getExtraParams()).thenReturn(extraParams);

        // Set up mock ticket returned by restCWClient
        when(restCWClient.get(config,"url")).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> ticketService.getCWTicket(config, CWTicket));
    }

    @Test
    void createTicket_withExpectedTicket_shouldNotThrowAnything() throws TalAdapterSyncException {
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        Map<String, String> extraParams = new HashMap<>(Map.of(
                "connectionFailed", "false",
                "synced", "false"
        ));
        when(CWTicket.getExtraParams()).thenReturn(extraParams);

        Assertions.assertDoesNotThrow(() -> ticketService.createTicket(config, CWTicket));
        // Should also set these extra params as so:
        Assertions.assertEquals("true", extraParams.get("synced"));
        Assertions.assertEquals("false", extraParams.get("connectionFailed"));
    }

    @Test
    void createTicket_fromTicketWithNoDescription_shouldModifySummary() throws TalAdapterSyncException {
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        Map<String, String> extraParams = new HashMap<>(Map.of(
                "connectionFailed", "false",
                "synced", "false"
        ));
        when(CWTicket.getExtraParams()).thenReturn(extraParams);
        when(CWTicket.getSummary()).thenCallRealMethod();
        when(CWTicket.setSummary(any(String.class))).thenCallRealMethod();

        Assertions.assertDoesNotThrow(() -> ticketService.createTicket(config, CWTicket));
        Assertions.assertFalse(CWTicket.getSummary().isEmpty());
        // Should also set these extra params as so:
        Assertions.assertEquals("true", extraParams.get("synced"));
        Assertions.assertEquals("false", extraParams.get("connectionFailed"));
    }

    @Test
    void createTicket_hasFailedBefore_shouldAppendWarningToSummary() {
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);
        when(CWTicket.getSummary()).thenReturn("ticket summary");
        Map<String, String> extraParams = new HashMap<>(Map.of(
                "connectionFailed", "true",
                "synced", "true"
        ));
        when(CWTicket.getExtraParams()).thenReturn(extraParams);

        Assertions.assertDoesNotThrow(() -> ticketService.createTicket(config, CWTicket));
        verify(CWTicket, times(1)).setSummary(any(String.class));
        // Should also set these extra params as so:
        Assertions.assertEquals("true", extraParams.get("synced"));
        Assertions.assertEquals("false", extraParams.get("connectionFailed"));
    }

    @Test
    void createTicket_whenCWClientPostThrowsException_shouldThrowSameException() throws TalAdapterSyncException {
        ConnectWiseTicket CWTicket = mock(ConnectWiseTicket.class);

        doThrow(notRecoverableException).when(restCWClient).post(any(TicketSystemConfig.class), any(ConnectWiseTicket.class));

        Assertions.assertThrows(TalAdapterSyncException.class, () -> ticketService.createTicket(config, CWTicket));
    }

    @Test
    void updateTicket_withExpectedTickets_shouldUpdateCWTicket() throws TalAdapterSyncException {
        // Create updated Symphony ticket
        Map<String, String> extraParams = new HashMap<>(Map.of(
                "connectionFailed", "false",
                "synced", "true"
        ));
        ConnectWiseTicket symphonyTicket = new ConnectWiseTicket("symphonyId","symphonyLink",
                "ConnectWiseId","url",extraParams);
        symphonyTicket.setSummary("Symphony summary");
        symphonyTicket.setStatus("Closed");
        symphonyTicket.setPriority("Priority 1");
        symphonyTicket.setAssignedTo("lucap");

        // Create CW refreshed ticket
        ConnectWiseTicket CWTicket = new ConnectWiseTicket("symphonyId","symphonyLink",
                "ConnectWiseId","url",extraParams);
        CWTicket.setSummary("CW summary");
        CWTicket.setStatus("Open");
        CWTicket.setPriority("Priority 2");
        CWTicket.setAssignedTo("craigs");

        Assertions.assertDoesNotThrow(() -> ticketService.updateTicket(config, symphonyTicket, CWTicket));
        // Ensure it made a patch call
        verify(restCWClient, times(1)).patch(any(TicketSystemConfig.class), any(String.class), any(String.class));
        Assertions.assertEquals(CWTicket.getSummary(), symphonyTicket.getSummary());
        Assertions.assertEquals("Symphony summary", symphonyTicket.getSummary());
        Assertions.assertEquals(CWTicket.getStatus(), symphonyTicket.getStatus());
        Assertions.assertEquals("Closed", symphonyTicket.getStatus());
        Assertions.assertEquals(CWTicket.getPriority(), symphonyTicket.getPriority());
        Assertions.assertEquals("Priority 1", symphonyTicket.getPriority());
        Assertions.assertEquals(CWTicket.getAssignee(), symphonyTicket.getAssignee());
        Assertions.assertEquals("lucap", symphonyTicket.getAssignee());
    }

    @Test
    void updateTicket_whenPatchFails_shouldThrowSameException() throws TalAdapterSyncException {
        ConnectWiseTicket CWTicket = mock();
        when(CWTicket.getSummary()).thenReturn("CW summary");
        when(CWTicket.setSummary(any(String.class))).thenReturn(true);
        ConnectWiseTicket symphonyTicket = mock();
        when(symphonyTicket.getSummary()).thenReturn("symphony summary");
        when(symphonyTicket.getUrl()).thenReturn("url");

        doThrow(notRecoverableException).when(restCWClient).patch(any(TicketSystemConfig.class), any(String.class), any(String.class));

        Assertions.assertThrows(
                TalAdapterSyncException.class,
                () -> ticketService.updateTicket(config, symphonyTicket, CWTicket)
        );
    }



}
