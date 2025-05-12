package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.*;

/**
 * Implements logic for creating/updating/retrieving tickets from ConnectWise.
 *
 * @author LucaP<br> Created on 28 Nov 2023
 * @since 5.8
 */
public class TicketServiceImpl {
    //* ----------------------------- VARIABLES ----------------------------- *//

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(TicketServiceImpl.class);

    /**
    * Instance of ConnectWiseClient that is responsible for the communication with ConnectWise
    */
    private ConnectWiseClient CWClient;


    //* ----------------------------- METHODS ----------------------------- *//

    public TicketServiceImpl(ConnectWiseClient CWClient) {
        this.CWClient = CWClient;
    }

    /**
     * Retrieves the latest information on ConnectWise
     * @param CWTicket Ticket to be retrieved
     * @return a new instance of ConnectWiseTicket with the latest information. Null if the connection fails
     * @throws TalAdapterSyncException if connection fails and has failed before for the same ticket
     */
    public ConnectWiseTicket getCWTicket(TicketSystemConfig config,ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Make sure ticket has extra params map
        if (CWTicket.getExtraParams() == null) {
            CWTicket.setExtraParams(new HashMap<>());
        }

        // Possible error
        TalAdapterSyncException connectionFailedError = null;
        // 404 error
        boolean Ticket404NotFound = false;

        ConnectWiseTicket refreshedCWTicket = null;
        // Attempt URL
        if (CWTicket.getUrl() != null && !CWTicket.getUrl().isBlank()) {
            try {
                refreshedCWTicket = CWClient.get(config, CWTicket.getUrl());
            } catch (TalAdapterSyncException e) {
                if (e.getHttpStatus() != null &&
                        e.getHttpStatus().toString().toLowerCase().contains("404")) {
                    logger.warn("Ticket not found: " + e.getHttpStatus());
                    Ticket404NotFound = true;
                } else {
                    connectionFailedError = e;
                }
            }
        }

        // If URL did not work
        if (CWTicket.getId() != null && !CWTicket.getId().isBlank() && refreshedCWTicket == null) {
            // Validation to make sure neither ID, URL, nor API Path is null
            String url = "";
            try {
                url = createURL(config, CWTicket);
                refreshedCWTicket = CWClient.get(config, url);
                CWTicket.setUrl(url);
            } catch (TalAdapterSyncException e) {
                if (e.getHttpStatus() != null &&
                        e.getHttpStatus().toString().toLowerCase().contains("404")) {
                    logger.warn("Ticket not found: " + e.getHttpStatus());
                    Ticket404NotFound = true;
                } else {
                    connectionFailedError = e;
                }
            }
        }

        if (refreshedCWTicket == null) { // If getCWTicket was unable to get ConnectWise's ticket
            // Warn of error
            logger.warn("getCWTicket: Failed to retrieve ticket from ConnectWise");

            // If it is a 404 error close Symphony ticket
            if (Ticket404NotFound) {
                CWTicket.getExtraParams().put("404","true");
                return CWTicket;
            }

            // If it has failed before:
            if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true")) {
                // Throw connectionFailedError
                if (connectionFailedError != null)
                    throw connectionFailedError;
                else
                    throw new TalAdapterSyncException(
                            "Unable to reach ConnectWise to sync ticket - connection has failed previously"
                    );
            }
            else { //else
                // If this is NOT the ticket's first sync
                if (CWTicket.getExtraParams().get("synced") == null ||
                        Objects.equals(CWTicket.getExtraParams().get("synced"), "true")) {
                    // Add connectionFailedParameter
                    if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        CWTicket.getExtraParams().replace("connectionFailed", "true");
                    }
                }
            }

        } else {
            // Set refreshedCWTicket's Symphony variables
            refreshedCWTicket.setSymphonyId(CWTicket.getSymphonyId());
            refreshedCWTicket.setSymphonyLink(CWTicket.getSymphonyLink());

            // Else the connection was successful
            if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                CWTicket.getExtraParams().replace("connectionFailed", "false");
            }
            // Ensure that ticket knows it has been synced properly
            if (CWTicket.getExtraParams().putIfAbsent("synced", "true") != null) {
                CWTicket.getExtraParams().replace("synced", "true");
            }
        }

        return refreshedCWTicket;
    }

    /**
     * Creates new ticket in ConnectWise with CWTicket's information.
     * Updates CWTicket with ConnectWise's url and ticket number
     * @param CWTicket ticket to post
     * @throws TalAdapterSyncException if post call fails
     */
    public void createTicket(TicketSystemConfig config,ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Make sure ticket has extra params
        if (CWTicket.getExtraParams() == null)
            CWTicket.setExtraParams(new HashMap<>());


        // Adding initial priority comment
        if (config!= null && config.getPriorityMappingForSymphony() != null) { // null check
            ConnectWiseComment initialPriorityComment = new ConnectWiseComment(null, null, null,
                    String.format("Initial ticket priority: %s",
                            getSymphonyPriority(config, CWTicket)), null);
            CWTicket.addComment(initialPriorityComment);
        }

        // Make sure summary is not null
        if (CWTicket.getSummary() == null) {
            // Try using description
            if (CWTicket.getDescription() != null && CWTicket.getDescription().getText() != null) {
                logger.info("createTicket: summary is null. Using ticket description as new summary.");
                CWTicket.setSummary(CWTicket.getDescription().getText());
            } else {
                // else use standard description:
                logger.info("createTicket: summary is null. Using standard summary for new ticket.");
                CWTicket.setSummary("NEW Symphony ticket");
            }
        }

        // CHANGE SUMMARY IF TICKET HAS FAILED
        if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true") && // If connectionFailed param exists
                Objects.equals(CWTicket.getExtraParams().get("synced"), "true")) { // If ticket is not new (has been synced before)
            CWTicket.setSummary("Failed to connect - " + CWTicket.getSummary());
        }

        // Create new ticket on ConnectWise
        logger.info("createTicket: Attempting to POST ticket on ConnectWise");
        CWClient.post(config, CWTicket);

        if (CWTicket.getExtraParams().putIfAbsent("synced", "true") != null) {
            // Make sure ticket knows it has been synced
            CWTicket.getExtraParams().replace("synced", "true");
        }

    }

    /**
     * Updates CW with the information in CWTicket
     * @param CWTicket Ticket with the latest information from Symphony
     * @param refreshedTicket Ticket to be updated
     * @throws TalAdapterSyncException if patch call(s) fail
     */
    public void updateTicket(TicketSystemConfig config, ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) throws TalAdapterSyncException {
        // HTTP PATCH
        String patchRequest = "";
        // summary
        patchRequest += UpdateSummary(CWTicket, refreshedTicket, patchRequest);
        // status
        patchRequest += UpdateStatus(CWTicket, refreshedTicket, patchRequest);
        // priority
        patchRequest += UpdatePriority(config, CWTicket, refreshedTicket, patchRequest);
        // assignee
        patchRequest += UpdateAssignee(CWTicket, refreshedTicket, patchRequest, config);
        // requester
        // TODO: patchRequest += UpdateRequester(CWTicket, patchRequest);

        if (!patchRequest.isEmpty()) {
            patchRequest = "[" + patchRequest + "]"; // Final request formatting
            logger.info("updateTicket: Making PATCH request");
            try {
                CWClient.patch(config, CWTicket.getUrl(), patchRequest);
            } catch (Exception e) {
                logger.error("updateTicket: PATCH request failed");
                throw e;
            }
        } else {
            logger.info("updateTicket: No updates needed");
        }

        patchDescription(config, CWTicket, refreshedTicket);

        CWClient.patchComments(config, CWTicket, refreshedTicket);
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

    /**
     * Creates patch string to Update ConnectWise value.
     * Updates Symphony if necessary.
     *
     * @param CWTicket Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     * @param patchRequest
     * @return PATCH string
     */
    private String UpdateSummary(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest) {
        String returnVal = "";

        // If summaries are not the same
        if (!Objects.equals( CWTicket.getSummary(), refreshedTicket.getSummary())) {
            if (refreshedTicket.setSummary( CWTicket.getSummary() )) {
                logger.info("updateSummary: updating CW summary");
                returnVal = " {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"summary\",\n" +
                        "        \"value\": \"" + CWTicket.getSummary() + "\"\n" +
                        "    }\n";

            } else if (CWTicket.getDescription() != null) { // if Symphony value is null
                logger.info("updateSummary: updating CW summary to symphony description");
                refreshedTicket.setSummary( CWTicket.getDescription().getText() ); // set summary to description
                returnVal = " {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"summary\",\n" +
                        "        \"value\": \"" + refreshedTicket.getSummary() + "\"\n" +
                        "    }\n";

            } else if ( refreshedTicket.getSummary() != null ) { // if Symphony has null values but CW doesn't - update Symphony
                logger.info("updateSummary: updating Symphony summary");
                CWTicket.setSummary( refreshedTicket.getSummary() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    /**
     * Creates patch string to Update ConnectWise value.
     * Updates Symphony if necessary.
     *
     * @param CWTicket Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     * @param patchRequest
     * @return PATCH string
     */
    private String UpdateStatus(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( refreshedTicket.getStatus(), CWTicket.getStatus() )) {
            String op = (refreshedTicket.getStatus() == null ? "add" : "replace");
            String previousStatus = refreshedTicket.getStatus();

            if ( refreshedTicket.setStatus(CWTicket.getStatus()) ) {
                logger.info("updateStatus: updating status from {} to {}", previousStatus, CWTicket.getStatus());
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"status/name\",\n" +
                        "        \"value\": \"" + CWTicket.getStatus() + "\"\n" +
                        "    }\n";
            } else {
                logger.info("updateStatus: updating Symphony status from {} to {}", CWTicket.getStatus(), refreshedTicket.getStatus());
                CWTicket.setStatus( refreshedTicket.getStatus() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    /**
     * Creates patch string to Update ConnectWise value.
     * Updates Symphony if necessary.
     *
     * @param CWTicket Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     * @param patchRequest
     * @return PATCH string
     */
    private String UpdatePriority(TicketSystemConfig config, ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( refreshedTicket.getPriority(), CWTicket.getPriority() )) {
            String op = (refreshedTicket.getPriority() == null ? "add" : "replace");
            String CWPriority = "null";

            if (config != null && config.getPriorityMappingForSymphony() != null) {
                CWPriority = Optional.ofNullable(
                        getSymphonyPriority(config, refreshedTicket)
                ).orElse("null");
            }

            if ( refreshedTicket.setPriority(CWTicket.getPriority()) ) {
                logger.info("updatePriority: updating CW priority from {} to {}",
                        CWPriority,
                        getSymphonyPriority(config, CWTicket));
                // Get priority ID based on priority name
                String priorityID = null;
                try {
                    priorityID = CWClient.getPriorityID(config, CWTicket.getPriority()); // Try to find matching priority in CW
                } catch (TalAdapterSyncException e) {
                    logger.error("UpdatePriority: Unable to find priority ID in ConnectWise with matching name.");
                }
                if (priorityID != null) { // If priority ID was found, set values
                    returnVal = " {\n" +
                            "        \"op\": \"" + op + "\",\n" +
                            "        \"path\": \"priority/id\",\n" +
                            "        \"value\": \"" + priorityID + "\"\n" +
                            "    }\n";

                    // Add comment for change in priority
                    String priorityChangeText = "Priority updated: " + CWPriority + " -> " +
                            getSymphonyPriority(config, CWTicket);
                    ConnectWiseComment priorityChange = new ConnectWiseComment(null, null, null, priorityChangeText,
                            null,
                            false, true, false);
                    CWTicket.addComment(priorityChange);
                } else {
                    logger.warn("UpdatePriority: No priority found in ConnectWise with matching name. The name must match exactly.");
                }
            } else {
                logger.info("updatePriority: updating Symphony priority from {} to {}",
                        getSymphonyPriority(config, CWTicket),
                        getSymphonyPriority(config, refreshedTicket));
                CWTicket.setPriority( refreshedTicket.getPriority() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    /**
     * Creates patch string to Update ConnectWise value.
     * Updates Symphony if necessary.
     *
     * @param CWTicket        Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     * @param patchRequest
     * @param config
     * @return PATCH string
     */
    private String UpdateAssignee(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest, TicketSystemConfig config) {
        String returnVal = "";

        if (CWTicket.getAssignee() != null) {
            if (!Objects.equals(refreshedTicket.getAssignee(), CWTicket.getAssignee())) {
                String op = (refreshedTicket.getAssignee() == null ? "add" : "replace");

                refreshedTicket.setAssignedTo(CWTicket.getAssignee());
                logger.info("updateAssignee: updating CW assignee");
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"owner/identifier\",\n" +
                        "        \"value\": \"" + CWTicket.getAssignee() + "\"\n" +
                        "    }\n";
            }
        } else {
            // Check the extra params to see if the assignee has changed
            String symphonyAssigneeEmail = CWTicket.getExtraParams().get("assignedTo");
            if (symphonyAssigneeEmail != null && !symphonyAssigneeEmail.isEmpty()) {
                // Get the CW Identifier
                String symphonyAssigneeIdentifier = CWClient.getUserIdentifier(config, null, symphonyAssigneeEmail);
                // If the users differ: Add the PATCH
                if (symphonyAssigneeIdentifier != null &&
                        !Objects.equals(symphonyAssigneeIdentifier, refreshedTicket.getAssignee())) {
                    String op = (refreshedTicket.getAssignee() == null ? "add" : "replace");

                    refreshedTicket.setAssignedTo(CWTicket.getAssignee());
                    logger.info("updateAssignee: updating CW assignee");
                    returnVal = " {\n" +
                            "        \"op\": \"" + op + "\",\n" +
                            "        \"path\": \"owner/identifier\",\n" +
                            "        \"value\": \"" + symphonyAssigneeIdentifier + "\"\n" +
                            "    }\n";
                }
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    /**
     * Updates ConnectWise's description with Symphony's description using a PATCH request.
     *
     * @param CWTicket Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     */
    private void patchDescription(TicketSystemConfig config, ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) throws TalAdapterSyncException {
        // null-check
        String commentUrlPattern = config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS);
        if (commentUrlPattern == null) {
            logger.error("patchDescription: URL Pattern to get comments property configuration cannot be null");
            throw new InvalidArgumentException("URL Pattern to get comments property configuration cannot be null");
        }
        // Check if CW Comment exists
        if (refreshedTicket.getDescription() == null) {
            // If CW does not have a description comment, create one
            logger.info("updateDescription: ConnectWise description comment not found. Creating new comment");
            CWClient.postDescription(config, CWTicket);
        }
        else {
            // Compare texts
            if (CWTicket.getDescription() != null &&
                    !Objects.equals(refreshedTicket.getDescription().getText(), CWTicket.getDescription().getText())) {
                // If not equal: Update text
                String body = "[ {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"text\",\n" +
                        "        \"value\": \"" + CWTicket.getDescription().getText() + "\"\n" +
                        "    }]";
                logger.info("updateDescription: Attempting PATCH request");
                try {
                    CWClient.patch(config, CWTicket.getUrl() + commentUrlPattern
                                           + "/" +
                                           refreshedTicket.getDescription().getThirdPartyId(),
                            body);
                } catch (TalAdapterSyncException e) {
                    logger.error("patchDescription: CW API Call error - unable to sync description. Http error code: {}",
                            e.getHttpStatus() != null ? e.getHttpStatus() : "not specified");
                }
            } else if (CWTicket.getDescription() == null) {
                // If they are equal or symphony doesn't exist:
                CWTicket.setDescription(refreshedTicket.getDescription() );
            }
        }
    }

    /**
     * Throws a TalAdapterSyncException if the URL formed using the parameters contains any nulls
     *
     * @throws TalAdapterSyncException if any part of the url is null
     * @return the formatted URL based on inputs
     */
    private String createURL(TicketSystemConfig config, ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // null checks
        if (CWTicket == null) {
            logger.error("createURL: CWTicket cannot be null");
            throw new InvalidArgumentException("CWTicket cannot be null");
        }
        if (config == null) {
            logger.error("createURL: config cannot be null");
            throw new TalAdapterSyncException("Config cannot be null");
        }
        if (config.getTicketSourceConfig() == null) {
            logger.error("createURL: ticket source config cannot be null");
            throw new TalAdapterSyncException("Ticket source config cannot be null");
        }

        Map<String, String> ticketSourceConfig = config.getTicketSourceConfig();

        if (ticketSourceConfig.get(TicketSourceConfigProperty.URL) == null ||
                ticketSourceConfig.get(TicketSourceConfigProperty.API_PATH) == null ||
                ticketSourceConfig.get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null) {
            // String of missing properties
            String missingProperties =
                    (ticketSourceConfig.get(TicketSourceConfigProperty.URL) == null?
                            " - URL" : "") +
                    (ticketSourceConfig.get(TicketSourceConfigProperty.API_PATH) == null?
                            " - API Path" : "") +
                    (ticketSourceConfig.get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null?
                            " - URL Pattern to get Ticket" : "");

            logger.error("createURL: required config properties are missing:" + missingProperties);
            throw new TalAdapterSyncException("config properties cannot be null:" + missingProperties,
                    HttpStatus.BAD_REQUEST);
        }
        if (CWTicket.getId() == null) {
            logger.error("createURL: CWTicket's ConnectWise ID cannot be null to form URL");
            throw new TalAdapterSyncException("CWTicket's ConnectWise ID cannot be null to form URL",
                    HttpStatus.BAD_REQUEST);
        }

        String url = ticketSourceConfig.get(TicketSourceConfigProperty.URL) +
                ticketSourceConfig.get(TicketSourceConfigProperty.API_PATH) +
                ticketSourceConfig.get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) + "/" +
                CWTicket.getId();

        return url;
    }

    /**
     * Gets the priority mapping for Symphony. If it fails using the config it uses the default mappings.
     * @param config The TicketSystemConfig with the mappings
     * @param CWTicket ticket to map the priority
     * @return String of mapped priority; "Priority mapping problem" if mapping failed.
     */
    private String getSymphonyPriority(TicketSystemConfig config, ConnectWiseTicket CWTicket) {
        String mappedPriority = Optional.of(config).map(TicketSystemConfig::getPriorityMappingForSymphony).map(a ->
                a.get(CWTicket.getPriority())).orElse(null);

        if (mappedPriority == null) {
            mappedPriority = Optional.ofNullable(DefaultTicketMappings.getPriorityMappingForSymphony())
                    .map(a -> a.get(CWTicket.getPriority()))
                    .orElse("Priority mapping problem");
        }

        return mappedPriority;
    }

    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//
}
