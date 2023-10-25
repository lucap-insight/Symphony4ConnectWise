/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.dto.*;
import com.avispl.symphony.api.tal.error.TalNotRecoverableException;
import com.avispl.symphony.api.tal.error.TalRecoverableException;
import com.insightsystems.symphony.tal.mocks.MockTalConfigService;
import com.insightsystems.symphony.tal.mocks.MockTalProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

//import org.apache.logging.log4j.Level;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import org.springframework.http.HttpStatus;

/**
 * Sample TAL adapter implementation.
 *
 * @author Symphony Dev Team<br> Created on 7 Dec 2018
 * @since 4.6
 */
public class TalAdapterImpl implements TalAdapter {

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(TalAdapterImpl.class);

    /**
     * Instance of a TalConfigService, set by Symphony via {@link #setTalConfigService(TalConfigService)}
     * In sake of testing simplicity, one may use MockTalConfigService provided with this sample
     */
    private TalConfigService talConfigService;

    /**
     * Instance of a TalProxy, set by Symphony via {@link #setTalProxy(TalProxy)}
     * In sake of testing simplicity, one may use MockTalProxy provided with this sample
     */
    private TalProxy talProxy;

    /**
     * Instance of TicketSystemConfig that contains mappings and destination
     * ticketing system configuration
     */
    private TicketSystemConfig config;

    /**
     * Account identifier - have to be provided to 3rd party adapter implementors by Symphony team
     */
    private UUID accountId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    /**
     * Default no-arg constructor
     */
    public TalAdapterImpl() {
        /**
         * Uncomment following in order to use mocks instead of setter-injected objects
         * Warning: use for development purposes only!
         */
        this.talConfigService = new MockTalConfigService();
        this.talProxy = new MockTalProxy();
    }

    /**
     * Called by Symphony automatically after instance of adapter is created and talConfigService/talProxy setters
     *
     * Important: In this method developer must not perform any heavy synchronous initialization or I/O bound operations.
     * All such operations must be performed asynchronously in background thread(s).
     */
    @Override
    public void init() {
        logger.info("Initializing Sample TAL adapter");

        // In order to get ticket updates from Symphony adapter must subscribe to this explicitly here
        // After subscription is done, all updates will come to this adapter instance via calls to syncTalTicket method
        talProxy.subscribeUpdates(accountId, this);

        try {
            // obtain adapter configuration
            setConfig(talConfigService.retrieveTicketSystemConfig(accountId));
        } catch (Exception e) {
            throw new RuntimeException("SampleTalAdapterImpl was unable to retrieve " +
                    "configuration from TalConfigService: " + e.getMessage(), e);
        }

        // subscribe for getting adapter configuration updates
        talConfigService.subscribeForTicketSystemConfigUpdate(accountId,
                (ticketSystemConfig) -> setConfig(ticketSystemConfig));

    }

    /**
     * Called by Symphony when application is about to exit
     */
    @Override
    public void destroy() {
        // destroy any persistent resources
        // such as thread pools or persistent connections
    }

    /**
     * Invoked on each ticket update that happens in Symphony
     * @param talTicket instance of ticket that contains updated data. Ticket always come containing all fields even those that didn't change
     * @return instance of TalTicket that contains thirdPartyId and thirdPartyLink set for ticket, comments and attachments provisioned in 3rd party system
     * @throws TalAdapterSyncException
     */
    @Override
    public TalTicket syncTalTicket(TalTicket talTicket) throws TalAdapterSyncException {
        try {
            System.out.println(talTicket);

            // Confirm that credentials have been set up
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN) == null ||
                    config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD) == null) {
                String errorMessage = "ConnectWise API Credentials missing:";
                if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN) == null)
                    errorMessage += " clientID";
                if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD) == null)
                    errorMessage += " Authorization";
                logger.error("syncTalTicket: " + errorMessage);
                throw new NullPointerException(errorMessage);
            }

            // Warnings
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null) {
                logger.warn("syncTalTicket: URL not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                logger.warn("syncTalTicket: API_PATH not setup on Config");
            }

            ConnectWiseTicket CWTicket = null;

            // map status, priorities, users to comply with 3rd party ticketing system
            try {
                CWTicket = TicketMapper.mapSymphonyToThirdParty(talTicket, config);
            } catch (NullPointerException e) {
                logger.error("syncTalTicket: error mapping Ticket info to CW equivalent");
                throw e;
            }

            // 1. make call to ConnectWise and get live ticket data
            ConnectWiseTicket refreshedCWTicket = CWTicket.refresh();

            // If CWTicket exists in CW
            if (refreshedCWTicket != null) {
                // Update it with the newest information
                refreshedCWTicket.patch(CWTicket);
                // Map ConnectWise ticket back to Symphony
                TicketMapper.mapThirdPartyToSymphony(talTicket, refreshedCWTicket, config);
            } else {
                // Otherwise, create new ticket
                CWTicket.post();
                logger.info("syncTalTicket: remapping ticket to Symphony");
                TicketMapper.mapThirdPartyToSymphony(talTicket, CWTicket, config);
            }

            logger.info("syncTalTicket: synchronization complete");
            return talTicket;

            /*

            // Setup information to connect to ConnectWise API
            String url = null; // this will hold the url to access the ticket
            JSONObject CWTicket = null; // This is the ConnectWise synced ticket

            // If the ConnectWise connection has been set using TalTicket's ThirdPartyLink
            // False if connection was set up using the ID or if a new ticket was created
            boolean connectionByLink = false;

            boolean createTicket = true; // If adapter needs to create ConnectWise ticket
            boolean connectionFailed = false; // If connection was attempted but failed

            // If ticket has Third Party ID and Third Party Link (already exists in ConnectWise)
            if (talTicket.getThirdPartyId() != null || talTicket.getThirdPartyLink() != null) {
                logger.info("syncTalTicket: Ticket has ID or Third Party link");

                // Try to access ticket via Third Party Link
                url = talTicket.getThirdPartyLink();

                // API call body
                logger.info("syncTalTicket: Attempting API call using Third Party Link");
                try {
                    CWTicket = ConnectWiseAPICall(url, "GET", null);
                    connectionByLink = true; // Connection was successful using ThirdPartyLink
                } catch (Exception e) {
                    logger.error("syncTalTicket: Attempt failed - " + e.getMessage());
                }

                // If response is null API call resulted in error: try manually building url
                if (CWTicket == null) { //FIXME: Perform some sort of verification on link
                    logger.info("syncTalTicket: Attempting API call using Third Party ID");

                    // Build url from config and ticket Third Party ID:
                    // URL example: "https://connect.myCompany.com.au"
                    // API_PATH example: "/v4_6_release/apis/3.0/service/tickets"
                    // ThirdPartyId example: "187204"
                    // url example: "https://connect.myCompany.com.au/v4_6_release/apis/3.0/service/tickets/187204"

                    url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                            config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                            "/" + talTicket.getThirdPartyId();

                    try {
                        CWTicket = ConnectWiseAPICall(url, "GET", null);
                    } catch (Exception e) {
                        logger.error("syncTalTicket: Attempt failed - " + e.getMessage());
                    }
                }

                // if response is still null API calls failed
                if (CWTicket == null) {
                    // Log error
                    logger.error("syncTalTicket: Both API attempts unsuccessful");

                    // Check if a connectionFailed already happen to prevent creating multiple tickets
                    if (Objects.equals(talTicket.getExtraParams().get("connectionFailed"), "true")) {
                        logger.info("synTalTicket: Ticket has failed before - not creating new ticket");
                        throw new TalAdapterSyncException("Cannot sync TAL ticket");
                    } else {
                        logger.info("syncTalTicket: Attempting to create new ticket");
                        connectionFailed = true;
                    }
                }
                // if response has value it means API call was successful
                else {
                    createTicket = false; // no need to create ticket anymore
                    logger.info("syncTalTicket: Attempt successful");

                    // Add extra parameter to show connection was successful (set connectionFailed to false)
                    if (talTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        talTicket.getExtraParams().replace("connectionFailed","false");
                    }
                }
            }

            // If ticket does not exist in ConnectWise: Create ticket in ConnectWise
            if (createTicket) {
                // See if connection was even attempted
                if (!connectionFailed) {
                    // If connection hasn't failed it means connection has not been attempted, so:
                    logger.info("syncTalTicket: Ticket has no ID and Third Party Link");
                } else {
                    // Add extra parameter to not duplicate ticket in case it happens again
                    logger.info("syncTalTicket: Setting the connectionFailed parameter to true");
                    if (talTicket.getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        talTicket.getExtraParams().replace("connectionFailed","true");
                    }
                    // Change description to note that the ticket has failed before
                    if (talTicket.getSubject() != null) {
                        talTicket.setSubject(talTicket.getSubject() + " - ERROR: previous ticket not found");
                    } else {
                        // FIXME: Hard coded summary standard
                        talTicket.setSubject("<Symphony> NEW Ticket - ERROR: previous ticket not found");
                    }
                }

                // Create new ticket on ConnectWise
                logger.info("syncTalTicket: Attempting to create ticket on ConnectWise");

                // Check if URL and API_PATH are not null
                if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null ||
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                    logger.error("syncTalTicket: URL or API_PATH not setup on Config");
                    throw new NullPointerException("Cannot create a new ticket: URL or API_PATH not setup on config");
                }

                url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH);

                // Body of the request
                // FIXME: Get board and company from ticketSourceConfig/TalConfigService
                String requestBody = "{\n" +
                        "    \"summary\" : \"" + talTicket.getSubject() + "\",\n" +
                        "    \"board\" : {\n" +
                        "        \"id\": 199\n" + // this should come from ticketSourceConfig or TalConfigService
                        "    },\n" +
                        "    \"company\": {\n" +
                        "        \"id\": 250\n" + // this should come from ticketSourceConfig or TalConfigService
                        "    },\n" +
                        "    \"priority\" : {\n" +
                        "        \"id\": "+ talTicket.getPriority() +"\n" +
                        "    },\n" +
                        //      "    \"contactEmailAddress\" : \"" + talTicket.getRequester() + "\"\n" +
                        "}";

                // Attempt creating ticket
                try {
                    CWTicket = ConnectWiseAPICall(url, "POST", requestBody);
                }
                // I can catch any exception because there is already error handling in the ConnectWiseAPICall method
                catch (Exception e) {
                    logger.error("syncTalTicket: Unable to POST ticket - {}", e.getMessage());
                    throw e;
                }

                // Setting URL to proper value with ticket id
                if (CWTicket != null) {
                    logger.info("syncTalTicket: setting TalTicket third party id");
                    url += "/" + CWTicket.get("id");
                    talTicket.setThirdPartyId(CWTicket.get("id") + "");
                    talTicket.setThirdPartyLink(url);
                }

                // Adding initial priority comment
                Comment initialPriorityComment = new Comment(null, null, null,
                        String.format("Initial ticket priority: %s",
                                config.getPriorityMappingForThirdParty().get(talTicket.getPriority())),
                        null);
                talTicket.getComments().add(initialPriorityComment);
            }


            // 2. handle response from 3rd party ticketing system

            // url should now be set to a valid value
            if (url == null) {
                throw new NullPointerException("An unexpected error occurred: URL is null");
            } else {
                logger.info("syncTalTicket: Connection set to: " + url);
            }


            // 3. if succeeded:
            //      change talTicket
            //      set thirdPartyId and thirdPartyLink
            //      set ticket summary (subject), priority, status and owner
            //      set comments and attachments provisioned in 3rd party system
            // Check and update:

            // Check if connection was established correctly
            if (CWTicket == null) {
                logger.info("syncTalTicket: ConnectWise ticket error");
                throw new NullPointerException("ConnectWise ticket is null");
            }

            // Ticket's third party link and ID check
            if (connectionByLink) {
                // Connection was successful using Link, now we need to try to check if the ID matches the link

                // Get ticket ID from link
                int beginIndex = talTicket.getThirdPartyLink().lastIndexOf('/') +1;
                String IDFromLink = talTicket.getThirdPartyLink().substring(beginIndex);

                if (!Objects.equals(IDFromLink, talTicket.getThirdPartyId())) {
                    // If ID is incorrect: fix it
                    logger.info("syncTalTicket: Fixing ThirdPartyID from: " +
                            talTicket.getThirdPartyId() + " to: " + IDFromLink);
                    talTicket.setThirdPartyId(IDFromLink);
                }
            } else {
                // This means Link is not functional but connection was successful using ThirdPartyID

                String testUrl = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                        "/" + talTicket.getThirdPartyId();

                if (!Objects.equals(testUrl, talTicket.getThirdPartyLink())) {
                    // If Url is incorrect: fix it
                    logger.info("syncTalTicket: Fixing ThirdPartyLink");
                    talTicket.setThirdPartyLink(testUrl);
                }
            }

            // Variable for body of PATCH request
            String requestBody = "";
            String path = null;
            String SymphonyValue = null;
            String ConnectWiseValue = null;


            //----- Ticket summary -----//

            path = "summary";
            SymphonyValue = talTicket.getSubject();
            try { // try to get ConnectWise value
                ConnectWiseValue = CWTicket.getString(path);
            } catch (JSONException e) { // It is possible that it does not exist on GET/POST response
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }

            // If there is no ConnectWise value and no Symphony value, ensure that there is a standard summary
            if (SymphonyValue == null && (ConnectWiseValue == null || Objects.equals(ConnectWiseValue, "null"))) {
                if (talTicket.getDescription() != null) {
                    // If ticket summary does not exist (Symphony or CW), use description instead
                    SymphonyValue = talTicket.getDescription();
                    talTicket.setSubject(SymphonyValue);
                    logger.info("syncTalTicket: Setting ticket summary to ticket description");
                } else {
                    // If ticket description also does not exist, use pre-set value for ticket summary
                    logger.info("syncTalTicket: Symphony ticket does not have summary or description. Using standard summary.");
                    // FIXME: Hard coded summary standard
                    SymphonyValue = "<Symphony> NEW Ticket";
                    talTicket.setSubject(SymphonyValue);
                }
            }

            // createRequestBody returns null if no update is needed
            String requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult != null)  { // So, if an update is needed:
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using ConnectWise value");
                    talTicket.setSubject(ConnectWiseValue);
                } else {
                    requestBody += requestResult;
                }
            }


            //----- Ticket priority -----//

            path = "priority/id";
            SymphonyValue = talTicket.getPriority();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("priority").getInt("id") + "";
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, false);
            if (requestResult != null) {
                // Current date time
                Date date = new Date();
                String oldPriority = null;
                String newPriority = null;

                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using ConnectWise value");

                    // Add comment saying ticket priority was changed
                    oldPriority = config.getPriorityMappingForThirdParty().get(talTicket.getPriority());
                    newPriority = config.getPriorityMappingForThirdParty().get(ConnectWiseValue);

                    talTicket.setPriority(ConnectWiseValue);

                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;

                    // Add comment saying ticket priority was changed
                    oldPriority = config.getPriorityMappingForThirdParty().get(ConnectWiseValue);
                    newPriority = config.getPriorityMappingForThirdParty().get(talTicket.getPriority());
                }
                logger.info("syncTalTicket: Adding comment about priority update");
                Comment priorityUpdateComment = new Comment(null, null, null,
                        String.format("Priority updated: %s -> %s", oldPriority, newPriority),
                        null);
                talTicket.getComments().add(priorityUpdateComment);
            }




            //----- Ticket status----- //

            path = "status/name";
            SymphonyValue = talTicket.getStatus();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("status").getString("name");
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult!= null) {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using ConnectWise value");
                    talTicket.setStatus(ConnectWiseValue);
                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;
                }
            }


            //----- User assigned to ticket -----//

            path = "owner/identifier";
            SymphonyValue = talTicket.getAssignedTo();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("owner").getString("identifier");
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult != null)  {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using ConnectWise value");
                    talTicket.setAssignedTo(ConnectWiseValue);
                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;
                }
            }

            // Attachments
            //logger.info("syncTalTicket: Updating ticket attachments");
            // TODO: Place to add attachments to ticket sync PATCH

            //System.out.println(requestBody);

            // PATCH
            if (!requestBody.isEmpty()) {
                requestBody = "[" + requestBody + "]"; // Final request formatting
                logger.info("syncTalTicket: Making PATCH request");
                try {
                    ConnectWiseAPICall(url, "PATCH", requestBody);
                } catch (Exception e) {
                    logger.error("syncTalTicket: PATCH request failed");
                    throw e;
                }
            } else {
                logger.info("syncTalTicket: No API call made");
            }

            // Comments
            logger.info("syncTalTicket: Updating ticket comments");
            syncComments(talTicket);


            // 4. return updated instance using "return statement" to the caller
            logger.info("synTalTicket: Synchronization complete");
            return talTicket;

             */

        }
        catch (TalAdapterSyncException r) {
            logger.error("Failed to sync ticket from TAL to InMemory Ticket System {}", talTicket);
            /*
            Recoverable exceptions:
                - TalAdapterSyncException:
                    - HTTP Status 408 - Time out
                    - HTTP Status 429 - Too many requests
                    - HTTP Status 502 - Bad gateway
                    - HTTP Status 503 - Service unavailable
             */
            List<Integer> RecoverableHttpStatus = new ArrayList<Integer>();
            RecoverableHttpStatus.add(408);
            RecoverableHttpStatus.add(429);
            RecoverableHttpStatus.add(502);
            RecoverableHttpStatus.add(503);

            if (r.getHttpStatus() != null && RecoverableHttpStatus.contains(r.getHttpStatus().value())) {
                throw new TalRecoverableException(r, talTicket);
            }

            throw new TalNotRecoverableException(r, talTicket);
        }
        catch (Exception e) {
            // Otherwise the method will change the error to a TalAdapterSyncException and add the information to the
            //error description
            logger.error("Failed to sync ticket from TAL to InMemory Ticket System {}", talTicket);

            throw new TalNotRecoverableException(e,talTicket);
        }
    }

    public void setTalConfigService(TalConfigService talConfigService) {
        this.talConfigService = talConfigService;
    }

    public TalConfigService getTalConfigService() {
        return this.talConfigService;
    }

    public void setTalProxy(TalProxy talProxy) {
        this.talProxy = talProxy;
    }

    public TalProxy getTalProxy() {
        return this.talProxy;
    }

    public TicketSystemConfig getConfig() {
        return config;
    }

    public void setConfig(TicketSystemConfig config) {
        this.config = config;
    }
}
