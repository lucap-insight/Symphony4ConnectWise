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
     * Instance of ConnectWiseClient that handles all communication with ConnectWise
     */
    private ConnectWiseClient CWClient;

    /**
     * Instance of TicketServiceImpl that handles the ticket logic
     */
    private TicketServiceImpl ticketService;

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

        // Add hardcoded priority and status mapping
        Map<String, String> customerPriorityMappingForSymphony  = new HashMap<>();
        customerPriorityMappingForSymphony.put("Priority 1", "Critical");
        customerPriorityMappingForSymphony.put("Priority 2", "Major");
        customerPriorityMappingForSymphony.put("Priority 3", "Minor");
        customerPriorityMappingForSymphony.put("Priority 4", "Informational");
        config.setPriorityMappingForSymphony(customerPriorityMappingForSymphony);
        Map<String, String> customerPriorityMappingForThirdParty  = new HashMap<>();
        customerPriorityMappingForThirdParty.put("Critical", "Priority 1");
        customerPriorityMappingForThirdParty.put("Major", "Priority 2");
        customerPriorityMappingForThirdParty.put("Minor", "Priority 3");
        customerPriorityMappingForThirdParty.put("Informational", "Priority 4");
        config.setPriorityMappingForThirdParty(customerPriorityMappingForThirdParty);
        Map<String, String> statusMappingForSymphony = new HashMap<>();
        statusMappingForSymphony.put("New", "New");
        statusMappingForSymphony.put("Open", "Open");
        statusMappingForSymphony.put("ClosePending", "ClosePending");
        statusMappingForSymphony.put("Close", "Close");
        config.setStatusMappingForSymphony(statusMappingForSymphony);
        Map<String, String> statusMappingForThirdParty = new HashMap<>();
        statusMappingForThirdParty.put("New", "New");
        statusMappingForThirdParty.put("Open", "Open");
        statusMappingForThirdParty.put("Close", "Close");
        statusMappingForThirdParty.put("ClosePending", "ClosePending");
        config.setStatusMappingForThirdParty(statusMappingForThirdParty);

        CWClient = new ConnectWiseClient(config);
        ticketService = new TicketServiceImpl(CWClient);
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
            //System.out.println(talTicket);
            if (talTicket == null)
                throw new InvalidArgumentException("talTicket cannot be null");

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
            ConnectWiseTicket refreshedCWTicket = ticketService.getCWTicket(CWTicket);

            // If CWTicket exists in CW
            if (refreshedCWTicket != null) {
                // Update it with the newest information
                ticketService.updateTicket(CWTicket, refreshedCWTicket);
                // Map ConnectWise ticket back to Symphony
                TicketMapper.mapThirdPartyToSymphony(talTicket, CWTicket, config);
            } else {
                // Otherwise, create new ticket
                ticketService.createTicket(CWTicket);
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
