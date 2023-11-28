package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TicketServiceImpl {
    //* ----------------------------- VARIABLES ----------------------------- *//

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
        throw new NotImplementedException();
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


    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//
}
