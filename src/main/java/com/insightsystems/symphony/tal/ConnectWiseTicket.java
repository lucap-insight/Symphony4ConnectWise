package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.*;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import com.avispl.symphony.api.tal.error.TalRecoverableException;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private TicketSystemConfig config;

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

    public ConnectWiseTicket(TicketSystemConfig config, String symphonyId, String symphonyLink, String id, String url, Map<String, String> extraParams) {
        // logger.info("Initializing ConnectWise ticket")
        setConfig(config);
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

    // TODO: DELETE
    public JSONObject ConnectWiseAPICall(String url, String method, String requestBody) throws TalAdapterSyncException {
        // Optional: Formalize input error checking on ConnectWiseAPICall

        String clientID = config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN);
        String authorization = config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD);

        if (clientID == null || authorization == null) {
            logger.error("ConnectWiseAPICall: Unable to retrieve client ID and/or authorization from configuration");
            throw new NullPointerException("Error retrieving client ID and/or authorization. Null value encountered");
        }

        if (url == null) {
            logger.error("ConnectWiseAPICall: URL cannot be null");
            throw new TalAdapterSyncException("URL for API call cannot be null");
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = null;

        try {
            if (requestBody != null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .build();
            } else if (Objects.equals(method, "GET")) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .build();
            }
        } catch (Exception e) {
            logger.error("ConnectWiseAPICall: Error building HttpRequest: " + e.getMessage());
            throw new TalAdapterSyncException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        // Response
        HttpResponse<String> response = null;
        //logger.info("ConnectWiseAPICall: Getting response");
        try {
            // Send HTTP request
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.error("ConnectWiseAPICall: HTTP request generated error: " + e.getMessage());
            if (response != null) {
                throw new TalAdapterSyncException(e + " - HTTP request error",
                        HttpStatus.valueOf(response.statusCode()));
            } else // Not recoverable. Without a response we can't be sure sending another request will fix it
                throw new TalAdapterSyncException(e + " - HTTP request error");
        }

        if (response != null && (response.statusCode() == 200 || response.statusCode() == 201)) {
            logger.info("ConnectWiseAPICall: {} call successful - HTTP Code: {}",
                    method,
                    response.statusCode());
        } else {
            // Log error
            logger.error("ConnectWiseAPICall: {} call failed - HTTP Code: {}",
                    method,
                    response != null ? response.statusCode() : "not specified");

            // Add HTTP status code response to error. It makes the error possibly recoverable
            throw new TalAdapterSyncException(method + " Request error",
                    response != null ? HttpStatus.valueOf(response.statusCode()) : null);
        }

        JSONObject jsonObject;
        try {
            //System.out.println(response.body());
            jsonObject = new JSONObject(response.body());
        } catch (JSONException e) {
            try {
                // It is possible that the response is a JSON array, so it is put in a JSON object under JSONArray
                jsonObject = new JSONObject("{ \"JSONArray\" : " + response.body() + "}");
            } catch (JSONException e2) {
                // If it is also not an Array: give up and report error
                logger.error("ConnectWiseAPICall: error parsing content to JSON - " + e2);
                logger.error("ConnectWiseAPICall: API call object: " + response.request());
                return null;
            }
        }
        return jsonObject;
    }

    // TODO: DELETE
    public void patch(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Update Symphony ID and Link
        setSymphonyId(CWTicket.getSymphonyId());
        setSymphonyLink(CWTicket.getSymphonyLink());

        // Create ticket in CW if it has failed before

        // HTTP PATCH
        String patchRequest = "";
        // summary
        patchRequest += UpdateSummary(CWTicket, patchRequest);
        // status
        patchRequest += UpdateStatus(CWTicket, patchRequest);
        // priority
        patchRequest += UpdatePriority(CWTicket, patchRequest);
        // assignee
        patchRequest += UpdateAssignee(CWTicket, patchRequest);
        // requester
        // TODO: patchRequest += UpdateRequester(CWTicket, patchRequest);

        if (!patchRequest.isEmpty()) {
            patchRequest = "[" + patchRequest + "]"; // Final request formatting
            logger.info("patch: Making PATCH request");
            try {
                ConnectWiseAPICall(url, "PATCH", patchRequest);
            } catch (Exception e) {
                logger.error("patch: PATCH request failed");
                throw e;
            }
        } else {
            logger.info("patch: No API call made");
        }

        // Description
        updateDescription(CWTicket);

        // Comments
        updateComments(CWTicket);


        // Attachments
        // Space for code
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

    // TODO: DELETE
    private String UpdateSummary(ConnectWiseTicket SymphonyTicket, String patchRequest) {
        String returnVal = "";

        // If summaries are not the same
        if (!Objects.equals( SymphonyTicket.getSummary(), getSummary())) {
            if (setSummary( SymphonyTicket.getSummary() )) {
                logger.info("updateSummary: updating CW summary");
                returnVal = " {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"summary\",\n" +
                        "        \"value\": \"" + SymphonyTicket.getSummary() + "\"\n" +
                        "    }\n";

            } else if (SymphonyTicket.getDescription() != null) { // if Symphony value is null
                logger.info("updateSummary: updating CW summary to symphony description");
                setSummary( SymphonyTicket.getDescription().getText() ); // set summary to description
                returnVal = " {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"summary\",\n" +
                        "        \"value\": \"" + getSummary() + "\"\n" +
                        "    }\n";

            } else if ( getSummary() != null ) { // if Symphony has null values but CW doesn't - update Symphony
                logger.info("updateSummary: updating Symphony summary");
                SymphonyTicket.setSummary( getSummary() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    // TODO: DELETE
    private String UpdateStatus(ConnectWiseTicket SymphonyTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( getStatus(), SymphonyTicket.getStatus() )) {
            String op = (getStatus() == null ? "add" : "replace");
            String previousStatus = getStatus();

            if ( setStatus(SymphonyTicket.getStatus()) ) {
                logger.info("updateStatus: updating status from {} to {}", previousStatus, SymphonyTicket.getStatus());
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"status/name\",\n" +
                        "        \"value\": \"" + SymphonyTicket.getStatus() + "\"\n" +
                        "    }\n";
            } else {
                logger.info("updateStatus: updating Symphony status from {} to {}", SymphonyTicket.getStatus(), getStatus());
                SymphonyTicket.setStatus( getStatus() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    // TODO: DELETE
    private String UpdatePriority(ConnectWiseTicket SymphonyTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( getPriority(), SymphonyTicket.getPriority() )) {
            String op = (getPriority() == null ? "add" : "replace");
            String CWPriority = config.getPriorityMappingForSymphony().get(getPriority());

            if ( setPriority(SymphonyTicket.getPriority()) ) {
                logger.info("updatePriority: updating CW priority from {} to {}",
                        CWPriority,
                        config.getPriorityMappingForSymphony().get(SymphonyTicket.getPriority()) );
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"priority/id\",\n" +
                        "        \"value\": \"" + SymphonyTicket.getPriority() + "\"\n" +
                        "    }\n";

                // Add comment for change in priority
                String priorityChangeText = "Priority updated: " + CWPriority + " -> " +
                        config.getPriorityMappingForSymphony().get(SymphonyTicket.getPriority());
                ConnectWiseComment priorityChange = new ConnectWiseComment(null, null, null, priorityChangeText,
                        null,
                        false, true, false);
                SymphonyTicket.addComment(priorityChange);

            } else {
                logger.info("updatePriority: updating Symphony priority from {} to {}",
                        config.getPriorityMappingForSymphony().get(SymphonyTicket.getPriority()),
                        config.getPriorityMappingForSymphony().get(getPriority()) );
                SymphonyTicket.setPriority( getPriority() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    // TODO: DELETE
    private String UpdateAssignee(ConnectWiseTicket SymphonyTicket, String patchRequest) {
        String returnVal = "";

        if (!Objects.equals( getAssignee(), SymphonyTicket.getAssignee() )) {

            String op = (getAssignee() == null ? "add" : "replace");

            if ( setAssignedTo(SymphonyTicket.getAssignee()) ) {
                logger.info("updateAssignee: updating CW assignee");
                returnVal = " {\n" +
                        "        \"op\": \"" + op + "\",\n" +
                        "        \"path\": \"owner/identifier\",\n" +
                        "        \"value\": \"" + SymphonyTicket.getAssignee() + "\"\n" +
                        "    }\n";
            } else {
                logger.info("updateAssignee: updating Symphony assignee");
                SymphonyTicket.setAssignedTo( getAssignee() );
            }
        }

        // Add , before if pathRequest had something
        if (!returnVal.isEmpty() && !patchRequest.isEmpty())
            returnVal = ",\n" + returnVal;

        return returnVal;
    }

    // TODO: DELETE
    private void updateDescription(ConnectWiseTicket SymphonyTicket) throws TalAdapterSyncException {
        // Check if CW Comment exists
        if (getDescription() != null)
        {
            // Compare texts
            if (SymphonyTicket.getDescription() != null &&
                    !Objects.equals(getDescription(), SymphonyTicket.getDescription()))
            {
                // If not equal: Update text and Symphony ID
                getDescription().setText( SymphonyTicket.getDescription().getText() );
                getDescription().setSymphonyId( SymphonyTicket.getSymphonyId() );
                // PATCH
                String body = "[ {\n" +
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"text\",\n" +
                        "        \"value\": \"" + SymphonyTicket.getDescription().getText() + "\"\n" +
                        "    }]";
                logger.info("updateDescription: Attempting PATCH request");
                try{
                    ConnectWiseAPICall(url + "/notes/" + getDescription().getThirdPartyId(),
                            "PATCH", body);

                } catch (TalAdapterSyncException e) {
                    logger.error("syncDescription: CW API Call error - unable to sync description. Http error code: {}",
                            e.getHttpStatus() != null ? e.getHttpStatus() : "not specified");
                }
            }
            else
            {
                // If they are equal or symphony doesn't exist:
                if (SymphonyTicket.getDescription() == null) // but sy
                    SymphonyTicket.setDescription( getDescription() );
                else
                    getDescription().setSymphonyId(SymphonyTicket.getDescription().getSymphonyId() );
            }
        } else {
            // If CW does not have a description comment, create one
            logger.info("updateDescription: ConnectWise description comment not found. Creating new comment");
            String description = "New Symphony ticket: No description found";
            if (SymphonyTicket.getDescription() != null) description = SymphonyTicket.getDescription().getText();
            String requestBody = "{\n" +
                    "    \"text\" : \"" + description + "\",\n" +
                    "    \"detailDescriptionFlag\": true" + // Set to default internal notes
                    (SymphonyTicket.getRequester() != null ? // make sure ticket requester is not null
                            "    ,\n" +
                                    "    \"member\": {\n" +
                                    "        \"identifier\": \"" + SymphonyTicket.getRequester() + "\"\n" +
                                    "    }\n"
                            : "\n") +
                    "}";
            try {
                logger.info("updateDescription: Attempting POST request");
                JSONObject newDescription = ConnectWiseAPICall(url + "/notes", "POST", requestBody);
                AddJSONDescription(newDescription);
            } catch (TalAdapterSyncException e) {
                logger.error("updateDescription: CW API Call error - unable to sync description. Http error code: {}",
                        e.getHttpStatus() != null ? e.getHttpStatus() : "not specified");
            }
        }
    }

    // TODO: DELETE
    private void updateComments(ConnectWiseTicket SymphonyTicket) {
        // Go for every Symphony ticket
        Set<ConnectWiseComment> commentsToPost = new HashSet<>();
        Iterator<ConnectWiseComment> itr = SymphonyTicket.getComments().iterator();
        ConnectWiseComment SymphonyComment;
        while ( itr.hasNext() ) {
            SymphonyComment = itr.next();
            boolean commentFound = false;

            // Check if ticket exists in ConnectWise
            for (ConnectWiseComment CWComment : getComments()) {
                // If it exists, and it's not the description: update CW ticket
                if (SymphonyComment.getThirdPartyId() != null && // if it has a CW ID
                        !Objects.equals( SymphonyComment.getThirdPartyId(), getDescription().getThirdPartyId() ) && // It's not the description
                        Objects.equals( CWComment.getThirdPartyId(), SymphonyComment.getThirdPartyId() ) ) { // And CW ID matches
                    commentFound = true;
                    CWComment.setSymphonyId( SymphonyComment.getSymphonyId() ); // Keep Symphony ID

                    // API call PATCH
                    if (!Objects.equals( SymphonyComment.getText(), CWComment.getText() )) { // if text is not the same
                        // Update text in TAL
                        CWComment.setText( SymphonyComment.getText() );

                        // Update text in CW
                        try {
                            logger.info("updateComments: Attempting to update comment");
                            String body = "[ {\n" +
                                    "        \"op\": \"replace\",\n" +
                                    "        \"path\": \"text\",\n" +
                                    "        \"value\": \"" + SymphonyComment.getText() + "\"\n" +
                                    "    }]";
                            ConnectWiseAPICall(url + "/notes/" + CWComment.getThirdPartyId(), "PATCH", body);
                        } catch (TalAdapterSyncException e) {
                            logger.error("updateComments: Attempt failed");
                        }
                    }
                }
            }

            if (!commentFound)
                commentsToPost.add(SymphonyComment);
        }

        if ( !commentsToPost.isEmpty() ) {
            // POST Ticket
            logger.info("updateComments: Posting {} new comments to ConnectWise",
                    commentsToPost.size());

            itr = commentsToPost.iterator();
            String requestBody = "";

            for ( ConnectWiseComment CWComment : commentsToPost ) {
                CWComment = itr.next();

                requestBody = "{\n" +
                        "    \"text\" : \"" + CWComment.getText() + "\",\n" +
                        "    \"internalAnalysisFlag\": true" + // Set to default internal notes
                        (CWComment.getCreator() != null ? // Make sure comment creator is not null
                                ",\n" +
                                        "    \"member\": {\n" +
                                        "        \"identifier\": \"" + CWComment.getCreator() + "\"\n" +
                                        "    }\n" : "\n") +
                        "}";

                try {
                    JSONObject jsonObject = ConnectWiseAPICall(url + "/notes", "POST", requestBody);
                    // Add ThirdParty ticket ID to ticket
                    logger.info("updateComments: POST Successful. Updating Comment ID on Symphony");
                    CWComment.setThirdPartyId(jsonObject.getInt("id") + "");
                    addComment(CWComment); // Add to CW ticket
                } catch (TalAdapterSyncException e) {
                    logger.error("updateComments: Unable to POST comment Symphony ID: {}. HTTP error: {}",
                            CWComment.getSymphonyId(),
                            e.getHttpStatus() != null ? e.getHttpStatus() : "not specified");
                }
            }
            logger.info("updateComments: Finished POSTing comments");
        } else {
            logger.info("updateComments: No comments to POST");
        }
    }


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

    public TicketSystemConfig getConfig() {
        return config;
    }

    public void setConfig(TicketSystemConfig config) {
        this.config = config;
    }


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
