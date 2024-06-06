package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import com.avispl.symphony.api.tal.error.TalRecoverableException;
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
import java.util.*;

public class ConnectWiseClient {

    //* ----------------------------- VARIABLES ----------------------------- *//

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectWiseClient.class);

    /**
     * Instance of TicketSystemConfig that contains mappings and destination
     * ticketing system configuration
     */
    private TicketSystemConfig config;

    /**
     * List of recoverable HTTP statuses
     */
    private List<Integer> RecoverableHttpStatus;

    /**
     * HTTP Client to send requests
     */
    private HttpClient client;


    //* ----------------------------- METHODS ----------------------------- *//

    /**
     * ConnectWiseClient empty constructor
     */
    public ConnectWiseClient(){
        RecoverableHttpStatus = new ArrayList<Integer>();
        RecoverableHttpStatus.add(408);
        RecoverableHttpStatus.add(429);
        RecoverableHttpStatus.add(502);
        RecoverableHttpStatus.add(503);

        this.client = HttpClient.newHttpClient();
    }

    /**
     * ConnectWiseClient constructor
     *
     * @param config fully configured instance of {@link TicketSystemConfig}
     */
    public ConnectWiseClient(TicketSystemConfig config) {
        if (config == null || config.getTicketSourceConfig() == null) {
            logger.error("ConnectWiseClient: attempted to create instance with null config or null TicketSourceConfig");
            throw new InvalidArgumentException("ConnectWiseClient cannot be instantiated with null config");
        }

        this.config = config;

        RecoverableHttpStatus = new ArrayList<Integer>();
        RecoverableHttpStatus.add(408);
        RecoverableHttpStatus.add(429);
        RecoverableHttpStatus.add(502);
        RecoverableHttpStatus.add(503);

        this.client = HttpClient.newHttpClient();
    }

    /**
     * Performs an HTTP request call to ConnectWise API using credentials set in config
     * @param url the HTTP request URI
     * @param method the HTTP method (i.e. GET)
     * @param requestBody the HTTP request's body
     * @return JSON object with the HTTP request response
     * @throws TalAdapterSyncException if request fails
     */
    private JSONObject ConnectWiseAPICall(String url, String method, String requestBody) throws TalAdapterSyncException {
        // Check for nulls
        if (config == null || config.getTicketSourceConfig() == null) {
            // Decided to use a Sync error because the config is not an argument (so not using an InvalidArgumentException)
            throw new TalAdapterSyncException("ConnectWiseClient config or ticketSourceConfig cannot be null");
        }
        // Optional: Formalize input error checking on ConnectWiseAPICall
        String clientID = config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.CLIENT_ID);
        String authorization = getBasicAuthenticationHeader();

        if (clientID == null || authorization == null) {
            logger.error("ConnectWiseAPICall: Unable to retrieve client ID and/or authorization from configuration");
            throw new TalAdapterSyncException("Error retrieving client ID and/or authorization. Null value encountered");
        }

        if (url == null) {
            logger.error("ConnectWiseAPICall: URL cannot be null");
            throw new InvalidArgumentException("URL for API call cannot be null");
        }


        HttpRequest request = null;

        // Set up Accept header
        String accept = "application/vnd.connectwise.com+json";
        if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.API_VERSION) != null) {
            accept += "; version=" + config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.API_VERSION);
        }

        try {
            if (requestBody != null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .header("Content-Type", "application/json")
                        .header("Accept", accept)
                        .build();
            } else if (Objects.equals(method, "GET")) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .header("Accept", accept)
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
                throw new TalAdapterSyncException("HTTP request error", HttpStatus.valueOf(response.statusCode()), e);
            } else // Not recoverable. Without a response we can't be sure sending another request will fix it
                throw new TalAdapterSyncException("HTTP request error", e);
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
            throw new TalAdapterSyncException(method + " Request error: " +
                    (response != null ?
                            response.body() + " HTTP " +  HttpStatus.valueOf(response.statusCode()) :
                            "no response body"),
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
     * @param url of the ticket to be refreshed
     * @return most updated version of Ticket retrieved from ConnectWise. Returns null if ticket is not on ConnectWise.
     * @throws TalAdapterSyncException if refresh fails
     */
    public ConnectWiseTicket get(String url) throws TalAdapterSyncException {
        // Attempt connection
        JSONObject response = null;
        ConnectWiseTicket refreshedCWTicket = null;

        logger.info("get: retrieving ticket");
        response = ConnectWiseAPICall(url, "GET", null);

        // If connection successful:
        if (response != null) {
            // Create new ticket and assign values
            refreshedCWTicket = new ConnectWiseTicket(response);
            refreshedCWTicket.setUrl(url);

            // Attempting to get comments
            logger.info("get: retrieving comments");
            boolean commentsFailed = false;
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS) == null) {
                logger.warn("get: URL Pattern to get Comments config property cannot be null");
                // FIXME: No error here? Sync should continue without syncing comments
                commentsFailed = true;
                //throw new TalAdapterSyncException("URL Pattern to get Comments config property cannot be null");
            }
            if (!commentsFailed) {
                try {
                    JSONArray jsonArray = ConnectWiseAPICall(
                            url + config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS),
                            "GET",
                            null)
                            .getJSONArray("JSONArray");
                    refreshedCWTicket.setComments(jsonArray);

                    // Set description
                    Optional<ConnectWiseComment> oldestDescriptionComment = refreshedCWTicket.getComments()
                            .stream()
                            .filter(ConnectWiseComment::isDescriptionFlag)
                            .min(Comparator.comparing(ConnectWiseComment::getLastModified));
                    if (oldestDescriptionComment.isPresent()) {
                        logger.info("get: ticket description found");
                        ConnectWiseComment description = oldestDescriptionComment.get();
                        refreshedCWTicket.setDescription(description);
                    } else {
                        logger.info("get: ticket description not found");
                    }
                } catch (TalAdapterSyncException e) {
                    logger.warn("get: unable to retrieve comments/description");
                }
            }
        }

        return refreshedCWTicket;
    }

    /**
     * Updates CWTicket based on requestBody with a PATCH API call
     *
     * @param url to update
     * @param requestBody PATCH call request body
     */
    public void patch(String url, String requestBody) throws TalAdapterSyncException {
        ConnectWiseAPICall(url, "PATCH", requestBody);
    }

    /**
     * Posts this ticket and its comments to ConnectWise.
     *
     * @param CWTicket ticket to post to ConnectWise
     * @throws TalAdapterSyncException if posting ticket failed
     */
    public void post(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Check if URL and API_PATH are not null
        if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_REC_ID) == null) {
            String missingProperties = "";
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null) {
                missingProperties += " - URL";
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                missingProperties += " - API path";
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET) == null) {
                missingProperties += " - URL Patter to get Ticket";
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_REC_ID) == null) {
                missingProperties += " - Company recID";
            }

            logger.error("post: required config properties are missing:" + missingProperties);
            throw new TalAdapterSyncException(
                    "Cannot create a new ticket: required config properties are missing:" + missingProperties);
        }

        // Warning if Board is null
        if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.BOARD_ID) == null) {
            logger.warn("post: Config's board is null. Ticket will be created on default board.");
        }

        String url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_TICKET);

        String requestBody = "{\n" +
                "    \"summary\" : \"" + CWTicket.getSummary() + "\",\n" +
                (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.BOARD_ID) != null ?
                "    \"board\" : {\n" +
                "        \"id\": "+
                        config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.BOARD_ID) + "\n" +
                "    },\n" : "") +
                "    \"company\": {\n" +
                "        \"id\": " +
                        config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_REC_ID) + "\n" +
                "    }" +
                (CWTicket.getStatus() != null ?
                ",\n" +
                "    \"status\" : {\n" +
                "        \"name\": \""+ CWTicket.getStatus() +"\"\n" +
                "    }" : "\n") +
                (CWTicket.getAssignee() != null ?
                ",\n" +
                "    \"owner\" : {\n" +
                "        \"identifier\": \""+ CWTicket.getAssignee() +"\"\n" +
                "    }" : "\n") +
                (CWTicket.getPriority() != null ?
                ",\n" +
                "    \"priority\" : {\n" + // TODO: Email about custom mapping?
                "        \"id\": "+ CWTicket.getPriority() +"\n" + // FIXME: Priority should be mapped by name, not id
                "    }\n" : "\n") +
                //      "    \"contactEmailAddress\" : \"" + talTicket.getRequester() + "\"\n" +
                "}";

        JSONObject response = ConnectWiseAPICall(url, "POST", requestBody);
        ConnectWiseTicket newTicket = new ConnectWiseTicket(response);
        newTicket.setUrl(url + "/" + newTicket.getId());

        // Update CWTicket
        CWTicket.setId(newTicket.getId());
        CWTicket.setUrl(newTicket.getUrl());

        // Update new ticket
        newTicket.setSymphonyId(CWTicket.getSymphonyId());
        newTicket.setSymphonyLink(CWTicket.getSymphonyLink());

        // POST description
        postDescription(CWTicket);

        // POST comments
        patchComments(CWTicket, newTicket);
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

    /**
     * Updates ConnectWise comments based on SymphonyTicket.
     * No comment flow from ConnectWise to Symphony.
     *
     * @param CWTicket ticket with updated Symphony information
     * @param newTicket ticket to be updated
     */
    public void patchComments(ConnectWiseTicket CWTicket, ConnectWiseTicket newTicket) throws TalAdapterSyncException {
        // null check
        if (CWTicket == null || newTicket == null)
            throw new InvalidArgumentException("CWTicket, newTicket and comments cannot be null");
        if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS) == null) {
            logger.error("patchComments: URL Pattern to get Comments config property cannot be null");
            throw new InvalidArgumentException("URL Pattern to get Comments config property cannot be null");
        }

        // CWTicket must have comment set
        if (CWTicket.getComments() == null)
            CWTicket.setComments(new HashSet<>());

        if (newTicket.getComments() == null)
            newTicket.setComments(new HashSet<>());

        String notesURL = newTicket.getUrl() +
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS);

        // Go for every Symphony ticket
        Set<ConnectWiseComment> commentsToPost = new HashSet<>();
        Iterator<ConnectWiseComment> itr = CWTicket.getComments().iterator();
        ConnectWiseComment SymphonyComment;
        while ( itr.hasNext() ) {
            SymphonyComment = itr.next();
            boolean commentFound = false;

            // Check if ticket exists in ConnectWise
            for (ConnectWiseComment CWComment : newTicket.getComments()) {
                // If it exists, and it's not the description: update CW ticket
                if (SymphonyComment.getThirdPartyId() != null && // if it has a CW ID
                        newTicket.getDescription() != null &&
                        !Objects.equals( SymphonyComment.getThirdPartyId(), newTicket.getDescription().getThirdPartyId() ) && // It's not the description
                        Objects.equals( CWComment.getThirdPartyId(), SymphonyComment.getThirdPartyId() ) ) { // And CW ID matches
                    commentFound = true;
                    CWComment.setSymphonyId( SymphonyComment.getSymphonyId() ); // Keep Symphony ID

                    // API call PATCH
                    if (!Objects.equals( SymphonyComment.getText(), CWComment.getText() )) { // if text is not the same
                        // Update text in TAL
                        CWComment.setText( SymphonyComment.getText() );

                        // Update text in CW
                        try {
                            logger.info("patchComments: Attempting to update comment");
                            String body = "[ {\n" +
                                    "        \"op\": \"replace\",\n" +
                                    "        \"path\": \"text\",\n" +
                                    "        \"value\": \"" + SymphonyComment.getText() + "\"\n" +
                                    "    }]";
                            ConnectWiseAPICall(notesURL + "/" + CWComment.getThirdPartyId(), "PATCH", body);
                        } catch (TalAdapterSyncException e) {
                            logger.error("patchComments: Attempt failed. HTTP error {} - {}",
                                    e.getHttpStatus() != null ? e.getHttpStatus() : "not specified",
                                    e.getMessage());
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

            String requestBody = "";
            int commentsToPostSize = commentsToPost.size();
            int commentNumber = 0;
            boolean anyCommentsFailed = false;
            TalAdapterSyncException lastException = null;

            for ( ConnectWiseComment CWComment : commentsToPost ) {
                commentNumber++;
                requestBody = "{\n" +
                        "    \"text\" : \"" + CWComment.getText() + "\",\n" +
                        "    \"detailDescriptionFlag\": " + CWComment.isDescriptionFlag() + ",\n" +
                        "    \"internalAnalysisFlag\": " + CWComment.isInternalFlag() + ",\n" +
                        "    \"resolutionFlag\": " + CWComment.isResolutionFlag() +
                        (CWComment.getCreator() != null ? // Make sure comment creator is not null
                            ",\n" +
                            "    \"member\": {\n" +
                            "        \"identifier\": \"" + CWComment.getCreator() + "\"\n" +
                            "    }\n" : "\n") +
                        "}";

                try {
                    JSONObject jsonObject = ConnectWiseAPICall(notesURL, "POST", requestBody);
                    // Add ThirdParty ticket ID to ticket
                    logger.info("updateComments: POST {}/{} Successful. Updating Comment ID on Symphony",
                            commentNumber,
                            commentsToPostSize);
                    CWComment.setThirdPartyId(jsonObject.getInt("id") + "");
                } catch (TalAdapterSyncException e) {
                    anyCommentsFailed = true;
                    lastException = e;
                    logger.error("updateComments: Unable to POST comment {}/{} - Symphony ID: {}. HTTP error: {}",
                            commentNumber,
                            commentsToPostSize,
                            CWComment.getSymphonyId(),
                            e.getHttpStatus() != null ? e.getHttpStatus() : "not specified");
                }
            }
            if (anyCommentsFailed) {
                logger.error("updateComments: unable to POST comment(s)");
                if (lastException != null)
                    throw new TalAdapterSyncException(lastException.getMessage() == null? "" : "");
            } else {
                logger.info("updateComments: Finished POSTing comments");
            }
        } else {
            logger.info("updateComments: No comments to POST");
        }
    }

    /**
     * Posts description comment in ConnectWise using the ticket's url
     *
     * @param CWTicket Ticket with description to be added to CW
     */
    public void postDescription(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        if (CWTicket == null)
            throw new InvalidArgumentException("CWTicket cannot be null");
        if (config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS) == null) {
            logger.error("postDescription: URL Pattern to get Comments config property cannot be null");
            throw new InvalidArgumentException("URL Pattern to get Comments config property cannot be null");
        }

        String description = "New Symphony ticket: No description found";
        if (CWTicket.getDescription() != null) description = CWTicket.getDescription().getText();
        String requestBody = "{\n" +
                "    \"text\" : \"" + description + "\",\n" +
                "    \"detailDescriptionFlag\": true,\n" + // It's the description
                "    \"internalAnalysisFlag\": false,\n" +
                "    \"resolutionFlag\": false" +
                (CWTicket.getRequester() != null ? // make sure ticket requester is not null
                    ",\n" +
                    "    \"member\": {\n" +
                    "        \"identifier\": \"" + CWTicket.getRequester() + "\"\n" +
                    "    }\n"
                    : "\n") +
                "}";

        try {
            logger.info("Attempting to POST ticket description");
            String url = CWTicket.getUrl() +
                    config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.URL_PATTERN_TO_GET_COMMENTS);
            JSONObject newDescription = ConnectWiseAPICall(url, "POST", requestBody);
            CWTicket.AddJSONDescription(newDescription);
        } catch (TalAdapterSyncException e) {
            logger.error("postDescription: Error posting description comment. Code {} - {}",
                    e.getHttpStatus() == null ? "not specified" : e.getHttpStatus(),
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Gets Basic authentication header for ConnectWise API in Base64
     *
     * @return Authentication header
     * @throws TalAdapterSyncException if config is not fully configured
     */
    private String getBasicAuthenticationHeader() throws TalAdapterSyncException {
        if (config == null ||
                config.getTicketSourceConfig() == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_ID) == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PUBLIC_KEY) == null ||
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PRIVATE_KEY) == null)
        {
            logger.error("getBasicAuthenticationHeader: Unable to retrieve Company ID/Public key/Private key from configuration");
            throw new TalAdapterSyncException("Config properties cannot be null");
        }
        String temp = config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.COMPANY_ID) + "+" +
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PUBLIC_KEY) + ":" +
                config.getTicketSourceConfig().get(TicketSourceConfigPropertyCW.PRIVATE_KEY);
        return "Basic " + Base64.getEncoder().encodeToString(temp.getBytes());
    }

    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//

    public List<Integer> getRecoverableHttpStatus() {
        return RecoverableHttpStatus;
    }

    public void setRecoverableHttpStatus(List<Integer> recoverableHttpStatus) {
        RecoverableHttpStatus = recoverableHttpStatus;
    }

    public TicketSystemConfig getConfig() {
        return config;
    }

    public void setConfig(TicketSystemConfig config) {
        this.config = config;
    }
}
