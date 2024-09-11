package com.insightsystems.symphony.tal;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that contains the default mappings between ConnectWise and Symphony
 *
 * @author LucaP<br> Created on 01 Aug 2024
 * @since 5.8
 */
public class DefaultTicketMappings {
    private static final Map<String, String> customerPriorityMappingForThirdParty = new HashMap<>();
    private static final Map<String, String> customerPriorityMappingForSymphony = new HashMap<>();
    private static final Map<String, String> statusMappingForSymphony = new HashMap<>();
    private static final Map<String, String> statusMappingForThirdParty = new HashMap<>();

    static {
        customerPriorityMappingForThirdParty.put("Critical", "Priority 1");
        customerPriorityMappingForThirdParty.put("Major", "Priority 2");
        customerPriorityMappingForThirdParty.put("Minor", "Priority 3");
        customerPriorityMappingForThirdParty.put("Informational", "Priority 4");

        customerPriorityMappingForSymphony.put("Priority 1", "Critical");
        customerPriorityMappingForSymphony.put("Priority 2", "Major");
        customerPriorityMappingForSymphony.put("Priority 3", "Minor");
        customerPriorityMappingForSymphony.put("Priority 4", "Informational");

        statusMappingForSymphony.put("Open", "Open");
        statusMappingForSymphony.put("ClosePending", "ClosePending");
        statusMappingForSymphony.put("Closed", "Closed");

        statusMappingForThirdParty.put("Open", "Open");
        statusMappingForThirdParty.put("Closed", "Closed");
        statusMappingForThirdParty.put("ClosePending", "ClosePending");
    }

    public static Map<String, String> getPriorityMappingForThirdParty() {
        return customerPriorityMappingForThirdParty;
    }
    public static Map<String, String> getPriorityMappingForSymphony() {
        return customerPriorityMappingForSymphony;
    }
    public static Map<String, String> getStatusMappingForThirdParty() {
        return statusMappingForThirdParty;
    }
    public static Map<String, String> getStatusMappingForSymphony() {
        return statusMappingForSymphony;
    }
}
