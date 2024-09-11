package tests;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import com.insightsystems.symphony.tal.ConnectWiseClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.insightsystems.symphony.tal.TicketSourceConfigPropertyCW;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.get(null));
    }

    @Test
    void get_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.get("url"));
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

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.patch(null, null));
    }

    @Test
    void patch_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.patch("url", "body"));
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

        Assertions.assertThrows(InvalidArgumentException.class, () -> restCWClient.post(null));
    }

    @Test
    void post_whenConfigIsMissing_shouldThrowTalAdapterSyncException() {
        when(config.getTicketSourceConfig()).thenReturn(null);

        Assertions.assertThrows(TalAdapterSyncException.class, () -> restCWClient.post(mock()));
    }

}
