package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.apache.commons.lang.NotImplementedException;
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

    public TicketServiceImpl() {} // TODO: Delete

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
        ConnectWiseTicket refreshedCWTicket = null;
        // Attempt URL
        if (CWTicket.getUrl() != null) {
            try {
                refreshedCWTicket = CWClient.get(CWTicket.getUrl());
            } catch (TalAdapterSyncException e) {
                if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true"))
                    throw e;
            }
        }

        // If URL did not work
        if (CWTicket.getId() != null && refreshedCWTicket == null) {
            // Validation to make sure neither ID, URL, nor API Path is null
            String url = validateUrl(CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.URL),
                    CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH),
                    CWTicket.getId()); // TODO: Make sure that the config is not null. Use code provided in the email

            try{
                refreshedCWTicket = CWClient.get(url);
                CWTicket.setUrl(url); // TODO: Write back in the email that this is actually right
            } catch (TalAdapterSyncException e) {
                if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true")) // TODO: null check getExtraParams()
                    throw e;
            }
        }

        if (refreshedCWTicket == null) {
            // Log error
            logger.warn("getNewestTicket: Both API attempts unsuccessful, adding connectionFailed extra parameter");
            // If it's the first time failing add connectionFailed parameter
            if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                CWTicket.getExtraParams().replace("connectionFailed", "true");
            }
        } else {
            // Else the connection was successful
            if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                CWTicket.getExtraParams().replace("connectionFailed", "false");
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
        // CHANGE SUMMARY IF TICKET HAS FAILED TODO: Null check .getExtraParams()
        if (CWTicket.getExtraParams().containsKey("connectionFailed") && // If connectionFailed param exists
                Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true") &&
                CWTicket.getExtraParams().containsKey("synced") && // If ticket is not new (has been synced before)
                Objects.equals(CWTicket.getExtraParams().get("synced"), "true")) {
            CWTicket.setSummary("Failed to connect - " + CWTicket.getSummary());
        }

        // Adding initial priority comment
        ConnectWiseComment initialPriorityComment = new ConnectWiseComment(null, null, null,
                String.format("Initial ticket priority: %s",
                        CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority())),
                null); // TODO: Null check .getPriorityMappingForSymphony()
        CWTicket.addComment(initialPriorityComment);

        // Create new ticket on ConnectWise
        logger.info("createTicket: Attempting to POST ticket on ConnectWise");
        CWClient.post(CWTicket);

        if (CWTicket.getExtraParams().putIfAbsent("synced", "true") != null) {
            // Make sure ticket know it has been synced
            CWTicket.getExtraParams().replace("synced", "true");
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
            logger.info("updateTicket: No API call made");
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
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"priority/id\",\n" +
                        "        \"value\": \"" + CWTicket.getPriority() + "\"\n" +
                        "    }\n";

                // Add comment for change in priority
                String priorityChangeText = "Priority updated: " + CWPriority + " -> " +
                        CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority());
                ConnectWiseComment priorityChange = new ConnectWiseComment(null, null, null, priorityChangeText,
                        null,
                        false, true, false);
                CWTicket.addComment(priorityChange);

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

    private void patchDescription(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) {
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
                    CWClient.patch(CWTicket.getUrl() + "/notes/" + refreshedTicket.getDescription().getThirdPartyId(),
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
    private String validateUrl(String url, String APIPath, String id) throws TalAdapterSyncException {
        if (url == null ||APIPath == null || id == null) {
            logger.error("refresh: Unable to form url - one of [URL, API_PATH, Ticket ID] is null");
            throw new TalAdapterSyncException("Unable to form URL with missing parameters:" +
                    (id == null ? " ID" : "") +
                    (url == null ? " URL":"") +
                    (APIPath == null? " API PATH":""),
                    HttpStatus.BAD_REQUEST);
        }
        return url + APIPath + "/" + id;
    }


    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//
}
