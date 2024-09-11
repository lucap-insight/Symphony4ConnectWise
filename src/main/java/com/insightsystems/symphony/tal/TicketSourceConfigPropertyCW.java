package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;

/**
 * List of config properties for the TAL adapter for ConnectWise.
 *
 * @author LucaP<br> Created on 28 Nov 2023
 * @since 5.8
 */
public interface TicketSourceConfigPropertyCW extends TicketSourceConfigProperty {
    /**
     * PUBLIC - ConnectWise API version, i.e. v2024.1
     */
    String API_VERSION = "apiVersion";
    /**
     * PUBLIC - REQUIRED - i.e. "/service/tickets"
     */
    String URL_PATTERN_TO_GET_TICKET = "urlPatternToGetTicket";
    /**
     * PUBLIC - REQUIRED - i.e. "/notes"
     */
    String URL_PATTERN_TO_GET_COMMENTS = "urlPatternToGetComments";
    /**
     * SECRET - REQUIRED - Used in clientID header
     */
    String CLIENT_ID = "clientId";
    /**
     * PUBLIC - REQUIRED - Used in Authorization header (sometimes the same as loginCompanyId)
     */
    String COMPANY_ID = "companyId";
    /**
     * SECRET - REQUIRED - Used in Authorization header
     */
    String PUBLIC_KEY = "publicKey";
    /**
     * SECRET - REQUIRED - Used in Authorization header
     */
    String PRIVATE_KEY = "privateKey";
    /**
     * PUBLIC - REQUIRED - company's ConnectWise recID for tickets in ConnectWise's database
     */
    String COMPANY_REC_ID = "companyRecId";
    /**
     * PUBLIC - ID of the ConnectWise board to post ticket to
     */
    String BOARD_ID = "boardId";
}
