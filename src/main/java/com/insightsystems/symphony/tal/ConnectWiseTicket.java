package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * Representation of ConnectWise ticket for TAL adapter
 *
 * @author LucaP<br> Created on 13 Sep 2023
 * @since 5.8
 */
public class ConnectWiseTicket {
    //* ----------------------------- VARIABLES ----------------------------- *//

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectWiseTicket.class);

    /**
     * Instance of TicketSystemConfig that contains mappings and destination
     * ticketing system configuration
     */
    //private TicketSystemConfig config;

    /**
     * Symphony unique ticket ID
     */
    private String symphonyId;

    /**
     * URL to a ticket in Symphony
     */
    private String symphonyLink;

    /**
     * ConnectWise unique ticket id
     */
    private String id;

    /**
     * URL to this ticket in ConnectWise
     */
    private String url;

    /**
     * Unique Symphony customer ID
     */
    private String customerId;

    /**
     * Ticket subject, short description of the ticket
     */
    private String summary;

    /**
     * Ticket description in a ConnectWise comment
     */
    private ConnectWiseComment description;

    /**
     * Ticket status
     */
    private String status;

    /**
     * Ticket priority
     */
    private String priority;

    /**
     * User identifier of ticket assignee
     */
    private String assignee;

    /**
     * Ticket requester user identifier
     */
    private String requester;

    /**
     * Ticket comments
     */
    private Set<ConnectWiseComment> Comments;

    /**
     * Ticket attachments
     */
    private Set<Attachment> Attachments;

    /**
     * extra parameters, dictionary of additional parameters in key-value form
     */
    private Map<String, String> extraParams;

    /**
     * List of recoverable HTTP statuses
     */
    private final List<Integer> RecoverableHttpStatus;


    //* ----------------------------- METHODS ----------------------------- *//

    public ConnectWiseTicket() {
        setAttachments(new HashSet<>());
        setComments(new HashSet<>());
        setExtraParams(new HashMap<>());
        RecoverableHttpStatus = new ArrayList<>();
    }

    public ConnectWiseTicket(String symphonyId, String symphonyLink, String id, String url, Map<String, String> extraParams) {
        // logger.info("Initializing ConnectWise ticket")
        setSymphonyId(symphonyId);
        setSymphonyLink(symphonyLink);
        setId(id);
        setUrl(url);
        setExtraParams(extraParams);
        setComments(new HashSet<>());

        RecoverableHttpStatus = new ArrayList<Integer>();
        RecoverableHttpStatus.add(408);
        RecoverableHttpStatus.add(429);
        RecoverableHttpStatus.add(502);
        RecoverableHttpStatus.add(503);
    }

    /**
     * Constructor from JSONObject
     * @param jsonObject ConnectWise JSON ticket
     */
    public ConnectWiseTicket(JSONObject jsonObject) {
        if (jsonObject == null)
            throw new InvalidArgumentException("ConnectWiseTicket cannot be instantiated with null jsonObject");

        // id
        try {
            setId(jsonObject.getInt("id") + "");

        } catch (JSONException e) {
            logger.info("id not found on jsonObject");
        }

        // summary
        try {
            setSummary(jsonObject.getString("summary"));
        } catch (JSONException e) {
            logger.info("summary not found on jsonObject");
        }

        // status
        try {
            setStatus(jsonObject.getJSONObject("status").getString("name"));
        } catch (JSONException e) {
            logger.info("status/name not found on jsonObject");
        }

        // priority
        try {
            setPriority(jsonObject.getJSONObject("priority").getInt("id") + "");
        } catch (JSONException e) {
            logger.info("priority/id not found on jsonObject");
        }

        // assignee
        try {
            setAssignedTo(jsonObject.getJSONObject("owner").getString("identifier"));
        } catch (JSONException e) {
            logger.info("owner/identifier not found on jsonObject");
        }

        // TODO: requester from ConnectWise
        /*try {
            CWJsonTicket.setSummary(jsonObject.getString("summary"));
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: summary not found on ConnectWise");
        }*/



        setComments(new HashSet<>());
        setAttachments(new HashSet<>());
        setExtraParams(new HashMap<>());
        RecoverableHttpStatus = new ArrayList<Integer>();
        RecoverableHttpStatus.add(408);
        RecoverableHttpStatus.add(429);
        RecoverableHttpStatus.add(502);
        RecoverableHttpStatus.add(503);
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//



    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//

    public String getSymphonyId() {
        return symphonyId;
    }

    public void setSymphonyId(String symphonyId) {
        this.symphonyId = symphonyId;
    }

    public String getSymphonyLink() {
        return symphonyLink;
    }

    public void setSymphonyLink(String symphonyLink) {
        this.symphonyLink = symphonyLink;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public boolean setUrl(String url) {
        if (url == null)
            return false;
        this.url = url;
        return true;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getSummary() {
        return summary;
    }

    public boolean setSummary(String summary) {
        if (summary == null)
            return false;
        int minIndex = Math.min(summary.length(), 100); // Cap summary size to 100
        this.summary = summary.substring(0, minIndex);
        return true;
    }

    public String getStatus() {
        return status;
    }

    public ConnectWiseComment getDescription() {
        return description;
    }

    public void setDescription(ConnectWiseComment description) {
        this.description = description;
    }

    public void AddJSONDescription(JSONObject newDescription) {
        ConnectWiseComment newCWDescription = null;
        try {
            DateTimeFormatter ConnectWiseDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:sX");
            LocalDateTime commentDate = LocalDateTime.parse(newDescription.getString("dateCreated"),
                    ConnectWiseDateTimeFormatter);
            ZonedDateTime zdt = ZonedDateTime.of(commentDate, ZoneId.systemDefault());
            long lastModified = zdt.toInstant().toEpochMilli();

            newCWDescription = new ConnectWiseComment(null, newDescription.getInt("id") + "",
                    newDescription.getString("createdBy"), newDescription.getString("text"), lastModified,
                    true, false, false);
        } catch (Exception e) {
            logger.error("AddJSONDescription: Error in parsing JSON information on CW Comment - {}", e.getMessage());
        }
        setDescription(newCWDescription);
    }

    public boolean setStatus(String status) {
        if (status == null)
            return false;
        this.status = status;
        return true;
    }

    public String getPriority() {
        return priority;
    }

    public boolean setPriority(String priority) {
        if (priority == null)
            return false;
        this.priority = priority;
        return true;
    }

    public String getAssignee() {
        return assignee;
    }

    public boolean setAssignedTo(String assignee) {
        if (assignee == null)
            return false;
        this.assignee = assignee;
        return true;
    }

    public String getRequester() {
        return requester;
    }

    public boolean setRequester(String requester) {
        if (requester == null)
            return false;
        this.requester = requester;
        return true;
    }

    public Set<ConnectWiseComment> getComments() {
        return Comments;
    }

    public void setComments(JSONArray jsonArray) {
        DateTimeFormatter ConnectWiseDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:sX");

        // for each ConnectWise comment:
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject JSONComment = jsonArray.getJSONObject(i);

            // Parse date
            LocalDateTime commentDate = LocalDateTime.parse(JSONComment.getString("dateCreated"),
                    ConnectWiseDateTimeFormatter);
            ZonedDateTime zdt = ZonedDateTime.of(commentDate, ZoneId.systemDefault());
            long lastModified = zdt.toInstant().toEpochMilli();

            ConnectWiseComment CWComment = new ConnectWiseComment(
                    null,
                    JSONComment.getInt("id") + "",
                    JSONComment.getString("createdBy"),
                    JSONComment.getString("text"),
                    lastModified,
                    JSONComment.getBoolean("detailDescriptionFlag"),
                    JSONComment.getBoolean("internalAnalysisFlag"),
                    JSONComment.getBoolean("resolutionFlag")
            );

            addComment(CWComment);
        }
    }

    public void setComments(Set<ConnectWiseComment> comments) {
        Comments = comments;
    }

    public void addComment(ConnectWiseComment CWComment) {
        Comments.add(CWComment);
    }

    public Set<Attachment> getAttachments() {
        return Attachments;
    }

    public void setAttachments(Set<Attachment> attachments) {
        Attachments = attachments;
    }

    public void addAttachment(Attachment CWAttachment) { Attachments.add(CWAttachment); }


    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, String> extraParams) {
        this.extraParams = extraParams;
    }

    public String toString() {
        return "ConnectWiseTicket{" +
                "symphonyId='" + getSymphonyId() + "', " +
                "symphonyLink='" + getSymphonyLink() + "', " +
                "thirdPartyId='" + getId() + "', " +
                "thirdPartyLink='" + getUrl() + "', " +
                "customerId='" + getCustomerId() + "', " +
                "priority='" + getPriority() + "', " +
                "status='" + getStatus() + "', " +
                "subject='" + getSummary() + "', " +
                "description='" + (getDescription() == null ? "null" : getDescription().getText()) + "', " +
                "requester='" + getRequester() + "', " +
                "assignedTo='" + getAssignee() + "', " +
                "extraParams=" + getExtraParams().toString() + ", " +
                "comments=" + getComments().toString() +
                "}";
    }
}
