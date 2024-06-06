package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;

public interface TicketSourceConfigPropertyCW extends TicketSourceConfigProperty {
    String API_VERSION = "apiVersion"; // PUBLIC - ConnectWise API version, i.e. v2024.1
    String LOGIN_COMPANY_ID = "loginCompanyId"; // PUBLIC - i.e. "connectwise"
    String URL_PATTERN_TO_GET_TICKET = "urlPatternToGetTicket"; // PUBLIC - REQUIRED - i.e. "/service/tickets"
    String URL_PATTERN_TO_GET_COMMENTS = "urlPatternToGetComments"; // PUBLIC - REQUIRED - i.e. "/comments"
    String CLIENT_ID = "clientId"; // SECRET - REQUIRED - Used in clientID header
    String COMPANY_ID = "companyId"; // PUBLIC - REQUIRED - Used in Authorization header (sometimes the same as loginCompanyId)
    String PUBLIC_KEY = "publicKey"; // SECRET - REQUIRED - Used in Authorization header
    String PRIVATE_KEY = "privateKey"; // SECRET - REQUIRED - Used in Authorization header
    String COMPANY_REC_ID = "companyRecId"; // PUBLIC - REQUIRED - company's recID for tickets in ConnectWise's database
    String BOARD_ID = "boardId"; // PUBLIC - ID of the board to post ticket to
}
