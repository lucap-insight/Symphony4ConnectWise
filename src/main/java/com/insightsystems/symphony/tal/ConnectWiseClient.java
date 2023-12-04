package com.insightsystems.symphony.tal;

import com.avispl.symphony.api.tal.dto.Comment;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
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

public class ConnectWiseClient {

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
     * List of recoverable HTTP statuses
     */
    private List<Integer> RecoverableHttpStatus;


    //* ----------------------------- METHODS ----------------------------- *//

    public ConnectWiseClient(){
        RecoverableHttpStatus = new ArrayList<Integer>();
        RecoverableHttpStatus.add(408);
        RecoverableHttpStatus.add(429);
        RecoverableHttpStatus.add(502);
        RecoverableHttpStatus.add(503);
    }

    public ConnectWiseClient(TicketSystemConfig config) {
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

    /**
     * Creates and returns a new ConnectWiseTicket instance with the newest ticket information from ConnectWise.
     * Does not alter the current instance.
     * Returns null if ticket was not found in ConnectWise.
     *
     * @param url of the ticket to be refreshed
     * @return most updated version of Ticket retrieved from ConnectWise. Returns null if ticket is not on ConnectWise.
     * @throws TalAdapterSyncException if refresh fails and has failed before
     */
    public ConnectWiseTicket GET(String url, ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        // Attempt connection
        JSONObject response = null;
        ConnectWiseTicket refreshedCWTicket = null;

        try {
            response = ConnectWiseAPICall(url, "GET", null);
        } catch (TalAdapterSyncException e) {
            logger.error("GET: connection failed. Error: {}", e.getMessage());

            // If connection already failed throw error
            if (Objects.equals(CWTicket.getExtraParams().get("connectionFailed"), "true"))
                throw e;
        }

        // If connection successful:
        if (response != null) {
            // Create new ticket and assign values
            refreshedCWTicket = new ConnectWiseTicket(
                    CWTicket.getConfig(), CWTicket.getSymphonyId(), CWTicket.getSymphonyLink(),
                    CWTicket.getId(), CWTicket.getUrl(), CWTicket.getExtraParams()
            );
            jsonToConnectWiseTicket(response, refreshedCWTicket);

            if (CWTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                CWTicket.getExtraParams().replace("connectionFailed","false");
            }

            // Attempting to get comments
            logger.info("GET: retrieving comments");
            JSONArray jsonArray = ConnectWiseAPICall(url + "/notes", "GET", null)
                    .getJSONArray("JSONArray");
            refreshedCWTicket.setComments(jsonToCommentSet(jsonArray));

            // Set description
            Optional<ConnectWiseComment> oldestDescriptionComment = refreshedCWTicket.getComments()
                    .stream()
                    .filter(ConnectWiseComment::isDescriptionFlag)
                    .min(Comparator.comparing(ConnectWiseComment::getLastModified));
            if (oldestDescriptionComment.isPresent()) {
                logger.info("GET: ticket description found");
                ConnectWiseComment description = oldestDescriptionComment.get();
                refreshedCWTicket.setDescription(description);
            } else {
                logger.info("GET: ticket description not found");
            }
        }

        return refreshedCWTicket;
    }

    /**
     * Updates this ticket based on CWTicket.
     * Makes changes to CWTicket as necessary.
     *
     * @param CWTicket ticket with the updated information
     */
    public void patch(ConnectWiseTicket CWTicket) throws TalAdapterSyncException {
        throw new NotImplementedException();
    }

    /**
     * Posts this ticket to ConnectWise.
     *
     * @throws TalAdapterSyncException if posting ticket failed
     */
    public void post() throws TalAdapterSyncException {
        throw new NotImplementedException();
    }


    //* ----------------------------- HELPER METHODS ----------------------------- *//

    /**
     * Converts an HTTP response in JSON format to a ConnectWiseTicket object
     *
     * @param jsonObject Ticket ConnectWise API response
     * @param CWJsonTicket ConnectWiseTicket to be infused with JSON information
     * @return converted ticket
     */
    private ConnectWiseTicket jsonToConnectWiseTicket(JSONObject jsonObject, ConnectWiseTicket CWJsonTicket) {
        // id
        try {
            CWJsonTicket.setId(jsonObject.getInt("id") + "");

            CWJsonTicket.setUrl(config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                    config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) + "/" +
                    CWJsonTicket.getId());
        } catch (JSONException e) {
            logger.info("jsonToConnectWiseTicket: id not found on ConnectWise");
        }

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
     * @param jsonArray JSON Array of CW comments
     * @return Set of Comments of the ConnectWise Comments
     */
    private Set<ConnectWiseComment> jsonToCommentSet(JSONArray jsonArray) {
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
