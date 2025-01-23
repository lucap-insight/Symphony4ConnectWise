package com.insightsystems.symphony.tal;


import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.avispl.symphony.api.tal.dto.UserIdMapping;
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

class TicketMapperTest {

    private static final String TAL_TICKET = "src/test/resources/talTicketSample1.json";

    private static TalTicket talTicket;

    private static TicketSystemConfig config;

    @BeforeAll
    public static void init() {
        try {
            talTicket = makeTalTicketFromJson(TAL_TICKET);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        config = new TicketSystemConfig();

        // keys are Symphony priorities and values are third party mapped statuses
        Map<String, String> customerPriorityMappingForThirdParty  = new HashMap<>();
        customerPriorityMappingForThirdParty.put("Critical", "Priority 1");
        customerPriorityMappingForThirdParty.put("Major", "Priority 2");
        customerPriorityMappingForThirdParty.put("Minor", "Priority 3");
        customerPriorityMappingForThirdParty.put("Informational", "Priority 4");

        // keys are third party priorities and values are Symphony mapped statuses
        Map<String, String> customerPriorityMappingForSymphony  = new HashMap<>();
        customerPriorityMappingForSymphony.put("Priority 1", "Critical");
        customerPriorityMappingForSymphony.put("Priority 2", "Major");
        customerPriorityMappingForSymphony.put("Priority 3", "Minor");
        customerPriorityMappingForSymphony.put("Priority 4", "Informational");

        // keys are third party users and values are Symphony mapped user
        Map<String, String> userMappingForSymphony = new HashMap<>();
        userMappingForSymphony.put("LPisano", "lucap@insightsystems.com.au");

        // keys are Symphony users and values are third party mapped users
        Map<String, UserIdMapping> userMappingForThirdParty = new HashMap<>();
        userMappingForThirdParty.put("lucap@insightsystems.com.au",
                new UserIdMapping("LPisano", "username"));

        // keys are third party statuses and values are Symphony mapped statuses
        Map<String, String> statusMappingForSymphony = new HashMap<>();
        statusMappingForSymphony.put("New", "Open");
        statusMappingForSymphony.put("Open", "Open");
        statusMappingForSymphony.put("ClosePending", "ClosePending");
        statusMappingForSymphony.put("Close", "Close");

        // keys are Symphony statuses and values are third party mapped statuses
        Map<String, String> statusMappingForThirdParty = new HashMap<>();
        statusMappingForThirdParty.put("Open", "Open");
        statusMappingForThirdParty.put("Close", "Close");
        statusMappingForThirdParty.put("ClosePending", "ClosePending");

        config.setPriorityMappingForThirdParty(customerPriorityMappingForThirdParty);
        config.setPriorityMappingForSymphony(customerPriorityMappingForSymphony);
        config.setUserMappingForSymphony(userMappingForSymphony);
        config.setUserMappingForThirdParty(userMappingForThirdParty);
//        config.setTicketSourceConfig(instanceConfigMapping); - Not used for ticket mapper
        config.setStatusMappingForSymphony(statusMappingForSymphony);
        config.setStatusMappingForThirdParty(statusMappingForThirdParty);

    }

    @AfterEach
    public void resetMocks() {
        // Reset the behavior of classB after each test
    }

    @Test
    void remapCWTicket_withoutModification_shouldReturnSameTicket() throws IOException {
        System.out.println(talTicket);
        ConnectWiseTicket CWTicket = TicketMapper.mapSymphonyToThirdParty(talTicket, config);
        TalTicket ticket = makeTalTicketFromJson(TAL_TICKET);
        TicketMapper.mapThirdPartyToSymphony(ticket, CWTicket, config);
        System.out.println(ticket);
		assertEquals(talTicket, ticket);
    }

    private static TalTicket makeTalTicketFromJson(String path) throws IOException {
        String talTicketJson = Files.readString(Path.of(path));
        // Had to add fail on unknown properties because some properties are not on my current version of the TAL adapter
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(talTicketJson, TalTicket.class);
    }
}
