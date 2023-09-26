package symphony.mock;

import com.avispl.symphony.api.tal.dto.TalTicket;
import com.insightsystems.symphony.tal.TalAdapterImpl;

public class MockSymphonyDriver {
    public static void main(String[] args) {

        // --- MOCK ENVIRONMENT SETUP --- //
        MockSymphony Symphony = new MockSymphony();

        // Instance of TalAdapter implementation is created
        TalAdapterImpl talAdapter = new TalAdapterImpl();

        Symphony.setTAL(talAdapter);

        // Symphony injects TalProxy and TalConfigService
        // This is on the SampleTalAdapterImpl constructor (delete after development)

        // Symphony calls init()
        talAdapter.init();


        // --- TEST TICKET UPDATE --- //


        int testNum = 0;

        // ---------------------------  TEST : PATCH --------------------------- //
        //TestCase(Symphony, Symphony.getTicket(), testNum, "PATCH TICKET");
        //testNum ++;

        // --------------------------- TEST : NO CHANGES ---------------------------//
        TestCase(Symphony, Symphony.getTicket(), testNum, "NO CHANGE TICKET");
        testNum ++;

        // ---------------------------  TEST : PATCH HALF --------------------------- //
        // TestCase(Symphony, Symphony.getTicketMissingInfo(), testNum, "PATCH HALF TICKET");
        //testNum ++;

        // --------------------------- TEST : POST --------------------------- //
        //TestCase(Symphony, Symphony.getTicket2(), testNum, "POST FULL TICKET"); //
        //testNum ++;

        // --------------------------- TEST : POST LACKING INFO ---------------------------//
        //TestCase(Symphony, Symphony.getTicketPOSTMissingInfo(), testNum, "POST HALF TICKET");
        //testNum ++;

        // --------------------------- TEST : WRONG TICKET --------------------------- //
        //TestCase(Symphony, Symphony.getWrongTicket(), testNum, "WRONG TICKET");
        //testNum ++;

        // --------------------------- TEST : FAILED TICKET --------------------------- //
        //TestCase(Symphony, Symphony.getFailedTicket(), testNum, "FAILED TICKET");
        //testNum ++;


    }

    static void TestCase(MockSymphony Symphony, TalTicket testTicket, int testNumber, String title) {
        System.out.println("\nMockSymphonyDriver: INITIALIZING TEST " + testNumber + " >> " + title);
        try {
            // Get symphony ticket
            System.out.println("MockSymphonyDriver: Created ticket: " + testTicket);

            // Symphony updates TAL
            TalTicket ThirdPartyTicket = Symphony.updateTal(testTicket);
            System.out.println("MockSymphonyDriver: Complete ticket: " + ThirdPartyTicket);
            System.out.println("MockSymphonyDriver: SUCCESS TEST " + testNumber + "\n");
        } catch (Exception e) {
            System.out.println("MockSymphonyDriver: FAIL TEST " + testNumber + " >> " + e.getMessage());
        }
    }
}
