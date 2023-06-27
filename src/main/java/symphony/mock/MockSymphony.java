package symphony.mock;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.dto.Attachment;
import com.avispl.symphony.api.tal.dto.Comment;
import com.avispl.symphony.api.tal.dto.TalTicket;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

import java.util.*;

public class MockSymphony{

    TalAdapter TAL;

    public MockSymphony() {

    }

    public void setTAL(TalAdapter TAL) {
        this.TAL = TAL;
    }

    /*
     * The next few methods with "getTicket[...]" are template Symphony tickets (talTickets). Before using them you
     * might want to change or delete the third party values as they are only mock values.
     *
     * Here is what each one of them does:
     *      - getTicket: Properly setup so that there are no changes when called on an existing ticket.
     *      - getTicket2: Fully setup talTicket but without any third party information (first time going to CW)
     *      - getTicketPOSTMissingInfo: talTicket with minimal information necessary to be synced to CW
     */

    /**
     * This getTicket is not a real Symphony method and is used only to return a pre-set TalTicket
     * @return properly setup TalTicket object
     */
    public TalTicket getTicket() {
        // Setting parameters for new ticket
        String symphonyId = "1067758";
        String symphonyLink = "1067758";
        String thirdPartyId = "187562";
        String thirdPartyLink = null;
        String customerId = "";
        String priority = "Minor";
        String status = "Open";
        String subject = "<TEST> test ticket for TAL";
        String description = "New TAL adapter test";
        String requester = "lucap@insightsystems.com.au";
        String assignedTo = "lucap@insightsystems.com.au";
        Set< Comment > comments = new HashSet<>();

        Set< Attachment > attachments = new HashSet<>();

        Map<String, String> extraParams = new HashMap<>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment patchComment = new Comment("00000", "262344", "lucap@insightsystems.com.au",
                "TAL Comment PATH NEW", lastModified);
        Comment postComment = new Comment("00001", null, "lucap@insightsystems.com.au",
                "TAL Comment POST", lastModified);
        Comment postComment2 = new Comment("00003", null, "lucap@insightsystems.com.au",
                "TAL Comment POST 2", lastModified);
        Comment initialDescription = new Comment("00002", "262346", "lucap@insightsystems.com.au",
                "Creating mock ticket for TAL Adapter test", lastModified);

        comments.add(patchComment);
        //comments.add(postComment);
        //comments.add(postComment2);
        comments.add(initialDescription);

        //extraParams.put("connectionFailed", "true");

        // Creating new ticket
        TalTicket newTicket = new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);

        return newTicket;
    }

    public TalTicket getTicket2() {
        // Setting parameters for new ticket
        String symphonyId = "1067759";
        String symphonyLink = "1067759";
        String thirdPartyId = null;
        String thirdPartyLink = null;
        String customerId = "";
        String priority = "Major";
        String status = "Open";
        String subject = "<TEST> Back to Symphony!";
        String description = "Creating a new CW ticket through Symphony again";
        String requester = "lucap@insightsystems.com.au";
        String assignedTo = "lucap@insightsystems.com.au";
        Set< Comment > comments = new HashSet<Comment>();

        Set< Attachment > attachments = new HashSet<Attachment>();

        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment postComment = new Comment("00001", null, null,
                "TAL NEW POST Test", lastModified);

        comments.add(postComment);


        // Creating new ticket
        return new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);
    }

    public TalTicket getTicketPOSTMissingInfo() {
        // Setting parameters for new ticket
        String symphonyId = "1067759";
        String symphonyLink = "1067759";
        String thirdPartyId = null;
        String thirdPartyLink = null;
        String customerId = null;
        String priority = null;
        String status = null;
        String subject = null;
        String description = null;
        String requester = null;
        String assignedTo = null;
        Set< Comment > comments = new HashSet<Comment>();

        Set< Attachment > attachments = new HashSet<Attachment>();

        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment postComment = new Comment("00001", null, "lucap@insightsystems.com.au",
                "TAL NEW POST Test", lastModified);

        comments.add(postComment);

        // Creating new ticket
        return new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);
    }


    public TalTicket updateTal(TalTicket ticket) {
        TalTicket ticketToReturn = null;

        if (TAL != null) {
            try {
                // capture returned ticket for testing purposes
                ticketToReturn = TAL.syncTalTicket(ticket);

            } catch (TalAdapterSyncException e) {
                System.out.println(String.format("SampleTalAdapterImpl was unable to retrieve " +
                        "configuration from TalConfigService: %s - %s", e.getMessage(), e.getHttpStatus()));
            }
        } else {
            throw new NullPointerException("TAL variable not set");
        }

        return ticketToReturn;
    }
}
