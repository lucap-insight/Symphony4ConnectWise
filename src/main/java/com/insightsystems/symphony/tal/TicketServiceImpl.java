package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.*;

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
    public ConnectWiseTicket getCWTicket(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Make sure ticket has extra params map
        if (CWTicket.getExtraParams() == null) {
            CWTicket.setExtraParams(new HashMap<>());
        }

        // Possible error
        TalAdapterSyncException connectionFailedError = null;

        ConnectWiseTicket refreshedCWTicket = null;
        // Attempt URL
        if (CWTicket.getUrl() != null) {
            try {
                refreshedCWTicket = CWClient.get(CWTicket.getUrl());
            } catch (TalAdapterSyncException e) {
                connectionFailedError = e;
            }
        }

        // If URL did not work
        if (CWTicket.getId() != null && refreshedCWTicket == null) {
            // Validation to make sure neither ID, URL, nor API Path is null
            String url = "";
            try {
                url = createURL(CWTicket);
                refreshedCWTicket = CWClient.get(url);
                CWTicket.setUrl(url);
            } catch (TalAdapterSyncException e) {
                connectionFailedError = e;
            }
        }

        if (refreshedCWTicket == null) { // If getCWTicket was unable to get ConnectWise's ticket
            // Warn of error
            logger.warn("getCWTicket: Failed to retrieve ticket from ConnectWise");

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
                // Add connectionFailedParameter
                if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                    // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                    CWTicket.getExtraParams().replace("connectionFailed", "true");
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
     * @throws TalAdapterSyncException
     */
    public void createTicket(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Make sure ticket has extra params
        if (CWTicket.getExtraParams() == null)
            CWTicket.setExtraParams(new HashMap<>());

        // CHANGE SUMMARY IF TICKET HAS FAILED
        if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true") && // If connectionFailed param exists
                Objects.equals(CWTicket.getExtraParams().get("synced"), "true")) { // If ticket is not new (has been synced before)
            CWTicket.setSummary("Failed to connect - " + CWTicket.getSummary());
        }


        // Adding initial priority comment
        if (CWClient.getConfig() != null && CWClient.getConfig().getPriorityMappingForSymphony() != null) { // null check
            ConnectWiseComment initialPriorityComment = new ConnectWiseComment(null, null, null,
                    String.format("Initial ticket priority: %s",
                            CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority())), null);
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

        // Create new ticket on ConnectWise
        logger.info("createTicket: Attempting to POST ticket on ConnectWise");
        CWClient.post(CWTicket);

        if (CWTicket.getExtraParams().putIfAbsent("synced", "true") != null) {
            // Make sure ticket knows it has been synced
            CWTicket.getExtraParams().replace("synced", "true");
        }
        if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
            CWTicket.getExtraParams().replace("connectionFailed", "false");
        }

    }

    /**
     * Updates CW with the information in CWTicket
     * @param CWTicket Ticket with the latest information from Symphony
     * @param refreshedTicket Ticket to be updated
     */
    public void updateTicket(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) throws TalAdapterSyncException {
        // HTTP PATCH
        String patchRequest = "";
        // summary
        patchRequest += UpdateSummary(CWTicket, refreshedTicket, patchRequest);
        // status
        patchRequest += UpdateStatus(CWTicket, refreshedTicket, patchRequest);
        // priority
        patchRequest += UpdatePriority(CWTicket, refreshedTicket, patchRequest);
        // assignee
        patchRequest += UpdateAssignee(CWTicket, refreshedTicket, patchRequest);
        // requester
        // TODO: patchRequest += UpdateRequester(CWTicket, patchRequest);

        if (!patchRequest.isEmpty()) {
            patchRequest = "[" + patchRequest + "]"; // Final request formatting
            logger.info("updateTicket: Making PATCH request");
            try {
                CWClient.patch(CWTicket.getUrl(), patchRequest);
            } catch (Exception e) {
                logger.error("updateTicket: PATCH request failed");
                throw e;
            }
        } else {
            logger.info("updateTicket: No updates needed");
        }

        patchDescription(CWTicket, refreshedTicket);

        CWClient.patchComments(CWTicket, refreshedTicket);
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
    private String UpdatePriority(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( refreshedTicket.getPriority(), CWTicket.getPriority() )) {
            String op = (refreshedTicket.getPriority() == null ? "add" : "replace");
            String CWPriority = CWClient.getConfig().getPriorityMappingForSymphony().get(refreshedTicket.getPriority());

            if ( refreshedTicket.setPriority(CWTicket.getPriority()) ) {
                logger.info("updatePriority: updating CW priority from {} to {}",
                        CWPriority,
                        CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority()) );
                // Get priority ID based on priority name
                String priorityID = null;
                try {
                    priorityID = CWClient.getPriorityID(CWTicket.getPriority()); // Try to find matching priority in CW
                } catch (TalAdapterSyncException e) {
                    logger.error("UpdatePriority: Unable to find priority ID in ConnectWise with matching name.");
                }
                if (priorityID != null) { // If priority ID was found, set values
                    returnVal = " {\n" + // FIXME: Priority patches need to be done by ID even when it is mapped to the name
                            "        \"op\": \"" + op + "\",\n" +
                            "        \"path\": \"priority/id\",\n" +
                            "        \"value\": \"" + priorityID + "\"\n" +
                            "    }\n";

                    // Add comment for change in priority
                    String priorityChangeText = "Priority updated: " + CWPriority + " -> " +
                            CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority());
                    ConnectWiseComment priorityChange = new ConnectWiseComment(null, null, null, priorityChangeText,
                            null,
                            false, true, false);
                    CWTicket.addComment(priorityChange);
                } else {
                    logger.warn("UpdatePriority: No priority found in ConnectWise with matching name. The name must match exactly.");
                }
            } else {
                logger.info("updatePriority: updating Symphony priority from {} to {}",
                        CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority()),
                        CWClient.getConfig().getPriorityMappingForSymphony().get(refreshedTicket.getPriority()) );
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
     * @param CWTicket Symphony ticket with the latest information
     * @param refreshedTicket Ticket retrieved from CW
     * @param patchRequest
     * @return PATCH string
     */
    private String UpdateAssignee(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( refreshedTicket.getAssignee(), CWTicket.getAssignee() )) {

            String op = (refreshedTicket.getAssignee() == null ? "add" : "replace");

            if ( refreshedTicket.setAssignedTo(CWTicket.getAssignee()) ) {
                logger.info("updateAssignee: updating CW assignee");
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"owner/identifier\",\n" +
                        "        \"value\": \"" + CWTicket.getAssignee() + "\"\n" +
                        "    }\n";
            } else {
                logger.info("updateAssignee: updating Symphony assignee");
                CWTicket.setAssignedTo( refreshedTicket.getAssignee() );
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
    private void patchDescription(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) throws TalAdapterSyncException {
        // null-check
        if (CWClient.getConfig()
                .getTicketSourceConfig()
                .get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS) == null) {
            logger.error("patchDescription: URL Pattern to get comments property configuration cannot be null");
            throw new InvalidArgumentException("URL Pattern to get comments property configuration cannot be null");
        }
        // Check if CW Comment exists
        if (refreshedTicket.getDescription() == null) {
            // If CW does not have a description comment, create one
            logger.info("updateDescription: ConnectWise description comment not found. Creating new comment");
            CWClient.postDescription(CWTicket);
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
                    CWClient.patch(CWTicket.getUrl() +
                                    CWClient.getConfig()
                                            .getTicketSourceConfig()
                                            .get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS)
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
    private String createURL(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // null checks
        if (CWTicket == null) {
            logger.error("createURL: CWTicket cannot be null");
            throw new InvalidArgumentException("CWTicket cannot be null");
        }
        if (CWClient.getConfig() == null) {
            logger.error("createURL: config cannot be null");
            throw new TalAdapterSyncException("Config cannot be null");
        }
        if (CWClient.getConfig().getTicketSourceConfig() == null) {
            logger.error("createURL: ticket source config cannot be null");
            throw new TalAdapterSyncException("Ticket source config cannot be null");
        }
        if (CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null ||
                CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null ||
                CWClient.getConfig().getTicketSourceConfig()
                        .get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null) {
            // String of missing properties
            String missingProperties =
                    (CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null?
                            " - URL" : "") +
                    (CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null?
                            " - API Path" : "") +
                    (CWClient.getConfig().getTicketSourceConfig()
                            .get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null?
                            " - URL Patter to get Ticket" : "");

            logger.error("createURL: required config properties are missing:" + missingProperties);
            throw new TalAdapterSyncException("config properties cannot be null:" + missingProperties,
                    HttpStatus.BAD_REQUEST);
        }
        if (CWTicket.getId() == null) {
            logger.error("createURL: CWTicket's ConnectWise ID cannot be null to form URL");
            throw new TalAdapterSyncException("CWTicket's ConnectWise ID cannot be null to form URL",
                    HttpStatus.BAD_REQUEST);
        }

        String url = CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                CWClient.getConfig().getTicketSourceConfig()
                        .get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) + "/" +
                CWTicket.getId();

        return url;
    }


    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//
}
