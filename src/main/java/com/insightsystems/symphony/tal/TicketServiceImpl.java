package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Objects;

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

    public TicketServiceImpl() {}

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
                    CWTicket.getId());

            try{
                refreshedCWTicket = CWClient.get(url);
            } catch (TalAdapterSyncException e) {
                if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true"))
                    throw e;
            }
        }

        if (refreshedCWTicket == null) {
            // Log error
            logger.error("getNewestTicket: Both API attempts unsuccessful, adding connectionFailed extra parameter");
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
     * @param CWTicket
     * @return updated CWTicket
     * @throws TalAdapterSyncException
     */
    public ConnectWiseTicket createTicket(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // CHANGE SUMMARY IF TICKET HAS FAILED
        if (CWTicket.getExtraParams().containsKey("connectionFailed") && // If connectionFailed param exists
                Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true")) { // If it's true
            CWTicket.setSummary("ERROR: previous ticket not found - " + CWTicket.getSummary());
        }

        // Adding initial priority comment
        ConnectWiseComment initialPriorityComment = new ConnectWiseComment(null, null, null,
                String.format("Initial ticket priority: %s",
                        CWClient.getConfig().getPriorityMappingForSymphony().get(CWTicket.getPriority())),
                null);
        CWTicket.addComment(initialPriorityComment);

        // Create new ticket on ConnectWise
        logger.info("createTicket: Attempting to POST ticket on ConnectWise");
        CWClient.post(CWTicket);

        // CWClient.post(JSON);
        throw new NotImplementedException();
    }

    /**
     * Updates CW with the information in CWTicket
     * @param CWTicket Ticket with the latest information from Symphony
     * @param refreshedTicket Ticket to be updated
     * @return
     */
    public ConnectWiseTicket updateTicket(ConnectWiseTicket CWTicket, ConnectWiseTicket refreshedTicket) throws TalAdapterSyncException {
        throw new NotImplementedException();
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

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
