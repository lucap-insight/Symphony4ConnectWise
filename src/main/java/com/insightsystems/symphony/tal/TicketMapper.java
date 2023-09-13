/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.dto.Attachment;
import com.avispl.symphony.api.tal.dto.Comment;
import com.avispl.symphony.api.tal.dto.TalTicket;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;

/**
 * Performs mapping of TAL ticket into third-party one and vice-versa
 *
 * @author Symphony Dev Team<br> Created on Dec 7, 2018
 * @since 4.6
 */
public class TicketMapper {

    /**
     * Converts a TAL ticket into a {@link ConnectWiseTicket}
     * and performs statuses/priorities/etc mapping.
     *
     * Note that the {@link TalTicket} model is used for both TAL and third-party tickets just to simplify the sample,
     * when integrating with a real ticket system, the appropriate class for third-party tickets should be used.
     *
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     * @return the mapped ticket
     */
    public static ConnectWiseTicket mapSymphonyToThirdParty(TalTicket ticket, TicketSystemConfig config)
    {
        ConnectWiseTicket CWTicket = new ConnectWiseTicket(
                config, ticket.getSymphonyId(), ticket.getSymphonyLink(), ticket.getThirdPartyId(),
                ticket.getThirdPartyLink()
        );

        mapTicketStatus(ticket, CWTicket, config);
        mapTicketPriority(ticket, CWTicket, config);
        mapRequestor(ticket, CWTicket, config);
        mapAssignee(ticket, CWTicket, config);
        mapCommentCreator(ticket, CWTicket, config);
        mapAttachmentCreator(ticket, CWTicket, config);

        return CWTicket;
    }

    /**
     * Maps ticket status from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapTicketStatus(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        String thirdPartyStatus = config.getStatusMappingForThirdParty().get(ticket.getStatus());

        if (thirdPartyStatus == null)
            return;

        CWTicket.setStatus(thirdPartyStatus);
    }

    /**
     * Maps ticket priority from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapTicketPriority(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        String thirdPartyPriority = config.getPriorityMappingForThirdParty().get(ticket.getPriority());

        if (thirdPartyPriority == null)
            return;

        CWTicket.setPriority(thirdPartyPriority);
    }

    /**
     * Maps ticket requestor from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapRequestor(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        CWTicket.setRequester(mapUser(ticket.getRequester(), config));
    }

    /**
     * Maps ticket assignee from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapAssignee(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        CWTicket.setAssignedTo(mapUser(ticket.getAssignedTo(), config));
    }

    /**
     * Maps comment requesters from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapCommentCreator(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        Optional.ofNullable(ticket.getComments())
                .orElse(Collections.emptySet())
                .stream()
                .forEach(c -> CWTicket.addComment(new Comment(c.getSymphonyId(), c.getThirdPartyId(),
                        mapUser(c.getCreator(), config), c.getText(), c.getLastModified())));

    }

    /**
     * Maps attachment requestors from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void mapAttachmentCreator(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        Optional.ofNullable(ticket.getAttachments())
                .orElse(Collections.emptySet())
                .stream()
                .forEach(c -> CWTicket.addAttachment(new Attachment(c.getSymphonyId(), c.getThirdPartyId(),
                        mapUser(c.getCreator(), config), c.getName(), c.getLink(), c.getSize(), c.getLastModified())));
    }

    /**
     * Maps user ID from Symphony to 3rd party ticketing system
     * @param userId user identifier to map
     * @param config adapter configuration
     * @return mapped identifier eligible for 3rd party ticketing system
     */
    private static String mapUser(String userId, TicketSystemConfig config) {
        if (userId == null)
            return null;

        String thirdPartyUserId = config.getUserMappingForThirdParty().get(userId).getThirdPartyId();

        if (thirdPartyUserId == null)
            return userId;

        return thirdPartyUserId;
    }
}
