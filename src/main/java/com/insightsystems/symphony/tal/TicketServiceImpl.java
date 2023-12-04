package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

public class TicketServiceImpl {
    //* ----------------------------- VARIABLES ----------------------------- *//

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectWiseTicket.class);

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
     * @return a new instance of ConnectWiseTicket with the latest information. Null if the ticket is not on CW.
     * @throws TalAdapterSyncException
     */
    public ConnectWiseTicket getNewestTicket(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        ConnectWiseTicket refreshedCWTicket = null;
        // Attempt URL
        if (CWTicket.getUrl() != null) {
            try {
                refreshedCWTicket = CWClient.GET(CWTicket.getUrl(), CWTicket);
            } catch (TalAdapterSyncException e) {
                logger.info("getNewestTicket: Ticket retrieval failed and it has failed before.");
                return null;
            }
        }

        // If URL did not work
        if (CWTicket.getId() != null && refreshedCWTicket != null) {
            try {
                // Validation to make sure neither ID, URL, nor API Path is null
                String url = validateUrl(CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.URL),
                        CWClient.getConfig().getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH),
                        CWTicket.getId());

                refreshedCWTicket = CWClient.GET(url, CWTicket);
            } catch (TalAdapterSyncException e) {
                logger.info("getNewestTicket: ticket retrieval failed");
                return null;
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
        // Prepare JSON

        // CWClient.post(JSON);
        throw new NotImplementedException();
    }

    /**
     * Updates CW with the information in CWTicket
     * @param CWTicket
     * @return
     */
    public ConnectWiseTicket updateTicket(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        throw new NotImplementedException();
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

    /**
     * Throws a TalAdapterSyncException if the URL formed using the parameters contains any nulls
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
        return url + APIPath + id;
    }

    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//
}
