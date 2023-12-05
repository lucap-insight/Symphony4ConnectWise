/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal;

import java.util.*;

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
        // Create new CWTicket
        ConnectWiseTicket CWTicket = new ConnectWiseTicket(
                ticket.getSymphonyId(), ticket.getSymphonyLink(), ticket.getThirdPartyId(),
                ticket.getThirdPartyLink(), ticket.getExtraParams()
        );

        CWTicket.setSummary(ticket.getSubject());
        mapTicketDescription(ticket, CWTicket, config);
        mapTicketStatus(ticket, CWTicket, config);
        mapTicketPriority(ticket, CWTicket, config);
        mapRequestor(ticket, CWTicket, config);
        mapAssignee(ticket, CWTicket, config);
        mapCommentCreator(ticket, CWTicket, config);
        mapAttachmentCreator(ticket, CWTicket, config);

        return CWTicket;
    }

    /**
     * Converts a {@link ConnectWiseTicket} into a {@link TalTicket}
     * and performs statuses/priorities/etc mapping.
     *
     * @param ticket ticket instance that needs to be mapped
     * @param config adapter configuration
     * @return the mapped ticket
     */
    public static TalTicket mapThirdPartyToSymphony(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config)
    {
        ticket.setSubject(CWTicket.getSummary());
        if (CWTicket.getDescription() != null) ticket.setDescription(CWTicket.getDescription().getText());
        ticket.setThirdPartyId(CWTicket.getId());
        ticket.setThirdPartyLink(CWTicket.getUrl());

        remapTicketStatus(ticket, CWTicket, config);
        remapTicketPriority(ticket, CWTicket, config);
        remapRequestor(ticket, CWTicket, config);
        remapAssignee(ticket, CWTicket, config);
        remapCommentCreator(ticket, CWTicket, config);
        remapAttachmentCreator(ticket, CWTicket, config);
        ticket.setExtraParams(CWTicket.getExtraParams());

        return ticket;
    }

    /**
     * Maps ticket description from Symphony to ConnectWise system
     * @param ticket ticket instance that needs to be mapped
     * @param CWTicket ticket to map status to
     * @param config adapter configuration
     */
    private static void mapTicketDescription(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        if ( ticket.getDescription() == null )
            return;

        ConnectWiseComment description = new ConnectWiseComment(ticket.getDescription());

        CWTicket.setDescription(description);
    }

    /**
     * Maps ticket status from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param CWTicket ticket to map status to
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
     * @param CWTicket ticket to map status to
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
     * @param CWTicket ticket to map status to
     * @param config adapter configuration
     */
    private static void mapRequestor(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        CWTicket.setRequester(mapUser(ticket.getRequester(), config));
    }

    /**
     * Maps ticket assignee from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param CWTicket ticket to map status to
     * @param config adapter configuration
     */
    private static void mapAssignee(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        CWTicket.setAssignedTo(mapUser(ticket.getAssignedTo(), config));
    }

    /**
     * Maps comment requesters from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param CWTicket ticket to map status to
     * @param config adapter configuration
     */
    private static void mapCommentCreator(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        Optional.ofNullable(ticket.getComments())
                .orElse(Collections.emptySet())
                .stream()
                .forEach(c -> CWTicket.addComment(new ConnectWiseComment(c.getSymphonyId(), c.getThirdPartyId(),
                        mapUser(c.getCreator(), config), c.getText(), c.getLastModified())));

    }

    /**
     * Maps attachment requestors from Symphony to 3rd party ticketing system
     * @param ticket ticket instance that needs to be mapped
     * @param CWTicket ticket to map status to
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

    /**
     * Maps user ID from Symphony to 3rd party ticketing system
     * @param userId user identifier to map
     * @param config adapter configuration
     * @return mapped identifier eligible for 3rd party ticketing system
     */
    private static String remapUser(String userId, TicketSystemConfig config) {
        if (userId == null)
            return null;

        String thirdPartyUserId = config.getUserMappingForSymphony().get(userId);

        if (thirdPartyUserId == null)
            return userId;

        return thirdPartyUserId;
    }

    /**
     * Maps ticket status from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapTicketStatus(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        String symphonyStatus = config.getStatusMappingForSymphony().get(CWTicket.getStatus());

        if (symphonyStatus == null)
            return;

        ticket.setStatus(symphonyStatus);
    }

    /**
     * Maps ticket priority from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapTicketPriority(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        String symphonyPriority = config.getPriorityMappingForSymphony().get(CWTicket.getPriority());

        if (symphonyPriority == null)
            return;

        ticket.setPriority(symphonyPriority);
    }

    /**
     * Maps ticket requester from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapRequestor(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        ticket.setRequester(remapUser(CWTicket.getRequester(), config));
    }

    /**
     * Maps ticket assignee from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapAssignee(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        ticket.setAssignedTo(remapUser(CWTicket.getAssignee(), config));
    }

    /**
     * Maps comment requesters from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapCommentCreator(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) {
        Set<Comment> symphonyComments = new HashSet<>();

        Optional.ofNullable(CWTicket.getComments())
                .orElse(Collections.emptySet())
                .stream()
                .forEach(c -> symphonyComments.add(new Comment(c.getSymphonyId(), c.getThirdPartyId(),
                        remapUser(c.getCreator(), config), c.getText(), c.getLastModified())));

        ticket.setComments(symphonyComments);
    }

    /**
     * Maps attachment requesters from ConnectWise to Symphony
     * @param ticket ticket to map status to
     * @param CWTicket ticket instance that needs to be mapped
     * @param config adapter configuration
     */
    private static void remapAttachmentCreator(TalTicket ticket, ConnectWiseTicket CWTicket, TicketSystemConfig config) { // TODO
        // space for attachment mapping
    }
}
