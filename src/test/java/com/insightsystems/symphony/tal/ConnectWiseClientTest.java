package com.insightsystems.symphony.tal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.util.Map;

import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

/**
 * Tester class for ConnectWiseClient.
 *
 * @author LucaP<br> Created on 28 Nov 2023
 * @since 5.8
 */
public class ConnectWiseClientTest {

    private static TicketSystemConfig config;
    private static HttpClient client;
    private static ConnectWiseClient restCWClient;

    @BeforeAll
    public static void init() {
        config = mock();
        restCWClient = new ConnectWiseClient(config);

        client = mock();

        ReflectionTestUtils.setField(restCWClient, "client", client);
    }

    @AfterEach
    public void resetMocks() {
        // Reset the behavior of classB after each test
        reset(config);
    }


    @Test
    void get_whenUrlIsNull_shouldThrowInvalidArgumentException() {

        Map<String, String> mapOfConfigs = Map.of(
                TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
                TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
                TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
                TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
        );
        when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.get(config,null));
    }

    @Test
    void get_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.get(config,"url"));
    }

    @Test
    void patch_whenUrlIsNull_shouldThrowInvalidArgumentException() {
        Map<String, String> mapOfConfigs = Map.of(
                TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
                TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
                TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
                TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId"
        );
        when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.patch(config,null, null));
    }

    @Test
    void patch_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.patch(config,"url", "body"));
    }

    @Test
    void post_whenTicketIsNull_shouldThrowInvalidArgumentException() {
        Map<String, String> mapOfConfigs = Map.of(
                TicketSourceConfigPropertyCW.CLIENT_ID, "mockClientId",
                TicketSourceConfigPropertyCW.PUBLIC_KEY, "mockPublicKey",
                TicketSourceConfigPropertyCW.PRIVATE_KEY, "mockPrivateKey",
                TicketSourceConfigPropertyCW.COMPANY_ID, "mockCompanyId",
                TicketSourceConfigPropertyCW.COMPANY_REC_ID, "mockCompanyRecId",
                TicketSourceConfigPropertyCW.URL, "mockUrl",
                TicketSourceConfigPropertyCW.API_PATH, "mockApiPath",
                TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET, "mockUrlPatternToGetTickets",
                TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS, "mockUrlPatternToGetComments"
        );
        when(config.getTicketSourceConfig()).thenReturn(mapOfConfigs);

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.post(config,null));
    }

    @Test
    void post_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.post(any(TicketSystemConfig.class),mock()));
    }

}
