/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal;

import java.util.*;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.dto.*;
import com.avispl.symphony.api.tal.error.TalNotRecoverableException;
import com.avispl.symphony.api.tal.error.TalRecoverableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

/**
 * Sample TAL adapter implementation.
 *
 * @author Symphony Dev Team<br> Created on 7 Dec 2018
 * @since 4.6
 */
public class ConnectWiseTalAdapter implements TalAdapter {
    public static final String ADAPTER_NAME = "ConnectWise";

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectWiseTalAdapter.class);

    /**
     * In sake of testing simplicity, one may use MockTalConfigService provided with this sample
     */
    private TalConfigService talConfigService;

    /**
     * In sake of testing simplicity, one may use MockTalProxy provided with this sample
     */
    private TalProxy talProxy;

    /**
     * Instance of TicketServiceImpl that handles the ticket logic
     */
    private TicketServiceImpl ticketService;

    /**
     * Account identifier - have to be provided to 3rd party adapter implementors by Symphony team
     */

    @Override
    public String getType() {
        return ADAPTER_NAME;
    }

    /**
     * Default no-arg constructor
     *
     * @param talConfigService Dependency injection for a {@link TalConfigService}
     * @param talProxy Dependency injection for a {@link TalProxy}
     */
    public ConnectWiseTalAdapter(TalConfigService talConfigService,
        TalProxy talProxy,
        TicketServiceImpl ticketService) {
        this.talConfigService = talConfigService;
        this.talProxy = talProxy;
        this.ticketService = ticketService;
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
     * @throws TalAdapterSyncException if sync failed. Throws a TalRecoverableException if error could be fixed by trying again.
     * Throws a TalNotRecoverableException if error is not recoverable.
     */
    @Override
    public TalTicket syncTalTicket(TalTicket talTicket) throws TalAdapterSyncException {
        try {
            if (talTicket == null)
                throw new InvalidArgumentException("talTicket cannot be null");

            if (talTicket.getCustomerId() == null) {
                throw new TalAdapterSyncException("talTicket's customer ID cannot be null");
            }
            TicketSystemConfig config = talConfigService.retrieveTicketSystemConfig(UUID.fromString(talTicket.getCustomerId()));

            // Confirm that credentials have been set up
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.CLIENT_ID) == null ||
                    config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PUBLIC_KEY) == null ||
                    config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PRIVATE_KEY) == null ||
                    config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_ID) == null) {
                String errorMessage = "ConnectWise API Credentials missing:";
                if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.CLIENT_ID) == null)
                    errorMessage += " clientID";
                if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PUBLIC_KEY) == null)
                    errorMessage += " Public key";
                if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PRIVATE_KEY) == null)
                    errorMessage += " Private key";
                if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_ID) == null)
                    errorMessage += " Company ID";
                logger.error("syncTalTicket: " + errorMessage);
                throw new TalAdapterSyncException(errorMessage);
            }

            // Warnings
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null) {
                logger.warn("syncTalTicket: URL not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                logger.warn("syncTalTicket: API_PATH not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null) {
                logger.warn("syncTalTicket: URL Pattern to get Ticket not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS) == null) {
                logger.warn("syncTalTicket: URL Pattern to get Comments not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_REC_ID) == null) {
                logger.warn("syncTalTicket: Company recID not setup on Config");
            }

            // Initialize components
            ConnectWiseTicket CWTicket = null;

            // map status, priorities, users to comply with 3rd party ticketing system
            try {
                CWTicket = TicketMapper.mapSymphonyToThirdParty(talTicket, config);
            } catch (Exception e) {
                logger.error("syncTalTicket: error mapping Ticket info to CW equivalent");
                throw new TalAdapterSyncException(e.getMessage(), e);
            }

            // 1. make call to ConnectWise and get live ticket data
            ConnectWiseTicket refreshedCWTicket = ticketService.getCWTicket(config, CWTicket);

            // If CWTicket exists in CW
            if (refreshedCWTicket != null) {
                // Update it with the newest information
                ticketService.updateTicket(config, CWTicket, refreshedCWTicket);
                // Map ConnectWise ticket back to Symphony
                TicketMapper.mapThirdPartyToSymphony(talTicket, CWTicket, config);
            } else {
                // Otherwise, create new ticket
                ticketService.createTicket(config, CWTicket);
                logger.info("syncTalTicket: remapping ticket to Symphony");
                TicketMapper.mapThirdPartyToSymphony(talTicket, CWTicket, config);
            }

            logger.info("syncTalTicket: synchronization complete");
            return talTicket;

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
            List<Integer> RecoverableHttpStatus = new ArrayList<>();
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
}
