package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.common.error.InvalidArgumentException;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;
import com.avispl.symphony.api.tal.error.TalNotRecoverableException;
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
        String clientID = config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN);
        String authorization = config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD);

        if (clientID == null || authorization == null) {
            logger.error("ConnectWiseAPICall: Unable to retrieve client ID and/or authorization from configuration");
            throw new TalAdapterSyncException("Error retrieving client ID and/or authorization. Null value encountered");
        }

        if (url == null) {
            logger.error("ConnectWiseAPICall: URL cannot be null");
            throw new InvalidArgumentException("URL for API call cannot be null");
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
            refreshedCWTicket.setUrl(config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                    config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) + "/" +
                    refreshedCWTicket.getId());

            // Attempting to get comments
            logger.info("get: retrieving comments");
            JSONArray jsonArray = ConnectWiseAPICall(url + "/notes", "GET", null)
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
                config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
            logger.error("post: URL or API_PATH not setup on Config");
            throw new TalAdapterSyncException("Cannot create a new ticket: URL or API_PATH not setup on config");
        }

        String url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH);

        // FIXME: Get board and company from ticketSourceConfig/TalConfigService
        String requestBody = "{\n" +
                "    \"summary\" : \"" + CWTicket.getSummary() + "\",\n" +
                "    \"board\" : {\n" +
                "        \"id\": 199\n" + // this should come from ticketSourceConfig or TalConfigService
                "    },\n" +
                "    \"company\": {\n" +
                "        \"id\": 250\n" + // this should come from ticketSourceConfig or TalConfigService
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
                "    \"priority\" : {\n" +
                "        \"id\": "+ CWTicket.getPriority() +"\n" +
                "    }\n" : "\n") +
                //      "    \"contactEmailAddress\" : \"" + talTicket.getRequester() + "\"\n" +
                "}";

        JSONObject response = ConnectWiseAPICall(url, "POST", requestBody);
        ConnectWiseTicket newTicket = new ConnectWiseTicket(response);
        newTicket.setUrl(url + "/" + newTicket.getId());

        // Update CWTicket
        CWTicket.setId(newTicket.getId());
        CWTicket.setUrl(newTicket.getUrl());

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
    public void patchComments(ConnectWiseTicket CWTicket, ConnectWiseTicket newTicket) {
        // null check
        if (CWTicket == null || newTicket == null)
            throw new InvalidArgumentException("CWTicket, newTicket and comments cannot be null");

        // CWTicket must have comment set
        if (CWTicket.getComments() == null)
            CWTicket.setComments(new HashSet<>());

        if (newTicket.getComments() == null)
            newTicket.setComments(new HashSet<>());

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
                            ConnectWiseAPICall(newTicket.getUrl() + "/notes/" + CWComment.getThirdPartyId(), "PATCH", body);
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

            for ( ConnectWiseComment CWComment : commentsToPost ) {

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
                    JSONObject jsonObject = ConnectWiseAPICall(CWTicket.getUrl() + "/notes", "POST", requestBody);
                    // Add ThirdParty ticket ID to ticket
                    logger.info("updateComments: POST Successful. Updating Comment ID on Symphony");
                    CWComment.setThirdPartyId(jsonObject.getInt("id") + "");
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

    /**
     * Posts description comment in ConnectWise using the ticket's url
     *
     * @param CWTicket Ticket with description to be added to CW
     */
    public void postDescription(ConnectWiseTicket CWTicket) {
        if (CWTicket == null)
            throw new InvalidArgumentException("CWTicket cannot be null");

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
            JSONObject newDescription = ConnectWiseAPICall(CWTicket.getUrl() + "/notes", "POST", requestBody);
            CWTicket.AddJSONDescription(newDescription);
        } catch (TalAdapterSyncException e) {
            logger.error("postDescription: Error posting description comment. Code {} - {}",
                    e.getHttpStatus() == null ? "not specified" : e.getHttpStatus(),
                    e.getMessage());
        }
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
