package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.*;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
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
    //-----------------------------------//
    //* ---------- VARIABLES ---------- *//
    //-----------------------------------//

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


    //---------------------------------//
    //* ---------- METHODS ---------- *//
    //---------------------------------//

    public ConnectWiseTicket() {
        setAttachments(new HashSet<>());
        setComments(new HashSet<>());
        setExtraParams(new HashMap<>());
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
    }

    // FIXME: See if this is possible and/or needed
    /*
    static public JSONObject ConnectWiseAPICall(String url, String method, String requestBody) throws TalAdapterSyncException {
        // Optional: Formalize input error checking on ConnectWiseAPICall

        // FIXME: What do I do with this? Where should I get this information from? How does CWTicket makes API calls?
        String clientID = config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN);
        String authorization = config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD);

        if (clientID == null || authorization == null) {
            logger.error("ConnectWiseAPICall: Unable to retrieve client ID and/or authorization from configuration");
            throw new NullPointerException("Error retrieving client ID and/or authorization. Null value encountered");
        }

        if (url == null) {
            logger.error("ConnectWiseAPICall: URL cannot be null");
            throw new NullPointerException("URL for API call cannot be null");
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
        logger.info("ConnectWiseAPICall: Getting response");
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

            if (response != null && response.statusCode() == 408) { // Re-try time-out calls
                // This will turn into a recoverable exception
                throw new TalAdapterSyncException("ConnectWiseAPICall: request timed-out", HttpStatus.REQUEST_TIMEOUT);
            }
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
    */

    /**
     * Performs an HTTP request call to ConnectWise API using credentials set in config
     * @param url the HTTP request URI
     * @param method the HTTP method (i.e. GET)
     * @param requestBody the HTTP request's body
     * @return JSON object with the HTTP request response
     * @throws TalAdapterSyncException if request fails
     */
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
            throw new NullPointerException("URL for API call cannot be null");
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
        logger.info("ConnectWiseAPICall: Getting response");
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

            if (response != null && response.statusCode() == 408) { // Re-try time-out calls
                // This will turn into a recoverable exception
                throw new TalAdapterSyncException("ConnectWiseAPICall: request timed-out", HttpStatus.REQUEST_TIMEOUT);
            }
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


    /**
     * Creates and returns a new ConnectWiseTicket instance with the newest ticket information from ConnectWise.
     * Does not alter the current instance.
     * Returns null if ticket was not found in ConnectWise.
     *
     * @return most updated version of this ticket retrieved from ConnectWise
     * @throws TalAdapterSyncException if refresh fails and has failed before
     */
    public ConnectWiseTicket refresh() throws TalAdapterSyncException { // TODO: Finalize refresh method
        // Declare synced ticket
        ConnectWiseTicket syncedCWTicket = null;

        // GET ticket
        if (this.getId() != null || this.getUrl() != null) {
            logger.info("refresh: Ticket has ID or Third Party link");

            // Attempt connection through URL
            logger.info("refresh: Attempting API call using Third Party Link");
            try {
                syncedCWTicket = jsonToConnectWiseTicket(ConnectWiseAPICall(getUrl(), "GET", null));

                // Update ticket number
                int lastIndex = getUrl().lastIndexOf('/');
                String temp_id;
                if (lastIndex >= 0 && lastIndex < getUrl().length() -1 ) {
                    temp_id = getUrl().substring(lastIndex + 1); // get ticket id from url
                    if (!Objects.equals(temp_id, getId())) { // if IDs don't match
                        setId(temp_id); // Update ticket id
                        syncedCWTicket.setId(temp_id);
                    }
                }

            } catch (Exception e) {
                logger.error("refresh: Attempt failed - " + e.getMessage());
            }

            // If response is null API call resulted in error: try manually building url
            if (syncedCWTicket == null) { //FIXME: Perform some sort of verification on link
                logger.info("refresh: Attempting API call using Third Party ID");

                // Build url from config and ticket Third Party ID:
                // URL example: "https://connect.myCompany.com.au"
                // API_PATH example: "/v4_6_release/apis/3.0/service/tickets"
                // ThirdPartyId example: "187204"
                // url example: "https://connect.myCompany.com.au/v4_6_release/apis/3.0/service/tickets/187204"

                String URL = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                        "/" + getId();

                try {
                    syncedCWTicket = jsonToConnectWiseTicket(ConnectWiseAPICall(URL, "GET", null));
                    setUrl(URL);
                    syncedCWTicket.setUrl(URL);
                } catch (Exception e) {
                    logger.error("refresh: Attempt failed - " + e.getMessage());
                }
            }

            // If an attempt has been made but still no connection, set connection failed
            if (syncedCWTicket == null) {
                // Log error
                logger.error("refresh: Both API attempts unsuccessful");

                // Check if a connectionFailed already happen to prevent creating multiple tickets
                if (Objects.equals(getExtraParams().get("connectionFailed"), "true")) {
                    logger.info("refresh: Ticket has failed before - not creating new ticket");
                    throw new TalAdapterSyncException("Cannot sync TAL ticket");
                } else {
                    // If it's the first time failing add connectionFailed parameter
                    if (getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        getExtraParams().replace("connectionFailed","true");
                    }
                }
            }
        }

        // TODO: GET comments on refresh
        if (syncedCWTicket != null) {
            logger.info("refresh: Attempt successful");

            // Add extra parameter to show connection was successful (set connectionFailed to false)
            if (getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                getExtraParams().replace("connectionFailed","false");
            }
            // Set extra parameter to show connection was successful on the synced ticket as well
            if (syncedCWTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                syncedCWTicket.getExtraParams().replace("connectionFailed","false");
            }

            JSONArray jsonArray = ConnectWiseAPICall(getUrl() + "/notes", "GET", null).getJSONArray("JSONArray");
            setComments(jsonToCommentSet(jsonArray));

            // Get description
            setDescription(
                    getComments()
                        .stream()
                        .filter(ConnectWiseComment::isDescriptionFlag)
                        .min( Comparator.comparing(ConnectWiseComment::getLastModified) ).get() );

            // TODO: Space to get ticket attachments as well
        }

        return syncedCWTicket;
    }

    /**
     * Updates this ticket based on CWTicket.
     * Makes changes to CWTicket as necessary.
     *
     * @param CWTicket ticket with the updated information
     */
    public void patch(ConnectWiseTicket CWTicket) { // TODO: Patch tickets
        // Update Symphony ID and Link
        setSymphonyId(CWTicket.getSymphonyId());
        setSymphonyLink(CWTicket.getSymphonyLink());

        // Create ticket in CW if it has failed before

        // HTTP PATCH
        // summary
        // status
        // priority
        // assignee
        // requester

        // Description

        // Comments

        // Attachments

        throw new NotImplementedException();
    }

    /**
     * Posts this ticket to ConnectWise.
     */
    public void post() throws TalAdapterSyncException { // TODO: Post ticket method
        // CHANGE SUMMARY IF TICKET HAS FAILED

        // Post new ticket
        throw new NotImplementedException("post");
    }

    // Update comments

    //


    /**
     * Converts an HTTP response in JSON format to a ConnectWiseTicket object
     *
     * @param jsonObject Ticket ConnectWise API response
     * @return converted ticket
     */
    private ConnectWiseTicket jsonToConnectWiseTicket(JSONObject jsonObject) { // TODO
        ConnectWiseTicket CWJsonTicket = new ConnectWiseTicket(getConfig(), getSymphonyId(), getSymphonyLink(), getId(), getUrl(), getExtraParams());

        // summary
        try {
            CWJsonTicket.setSummary(jsonObject.getString("summary"));
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: summary not found on ConnectWise");
        }

        // status
        try {
            CWJsonTicket.setStatus(jsonObject.getJSONObject("status").getString("name"));
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: status/name not found on ConnectWise");
        }

        // priority
        try {
            CWJsonTicket.setPriority(jsonObject.getJSONObject("priority").getInt("id") + "");
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: priority/id not found on ConnectWise");
        }

        // assignee
        try {
            CWJsonTicket.setAssignedTo(jsonObject.getJSONObject("owner").getString("identifier"));
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: owner/identifier not found on ConnectWise");
        }

        // TODO: requester from ConnectWise
        /*try {
            CWJsonTicket.setSummary(jsonObject.getString("summary"));
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: summary not found on ConnectWise");
        }*/

        return CWJsonTicket;
    }

    /**
     * Converts a JSON array of ConnectWise comments into a set of {@link Comment}s
     *
     * @param jsonArray
     * @return Set of Comments of the ConnectWise Comments
     */
    private Set<ConnectWiseComment> jsonToCommentSet(JSONArray jsonArray) { // TODO
        Set<ConnectWiseComment> JSONComments = new HashSet<>();
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

            JSONComments.add(CWComment);
        }

        return JSONComments;
    }



    //-------------------------------------------//
    //* ---------- GETTERS / SETTERS ---------- *//
    //-------------------------------------------//

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
        this.summary = summary;
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
}
