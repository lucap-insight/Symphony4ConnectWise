package com.insightsystems.symphony.tal;

public class ConnectWiseComment {
    //* ----------------------------- VARIABLES ----------------------------- *//

    private String symphonyId;

    private String thirdPartyId;

    private String creator;

    private String text;

    private long lastModified;

    private boolean descriptionFlag;

    private boolean internalFlag;

    private boolean resolutionFlag;


    //* ----------------------------- METHODS ----------------------------- *//

    public ConnectWiseComment(String text) {
        setText(text);
        setDescriptionFlag(false);
        setInternalFlag(true);
        setResolutionFlag(false);
    }

    public ConnectWiseComment(String symphonyId, String thirdPartyId, String creator, String text, Long lastModified,
                              boolean descriptionFlag, boolean internalFlag, boolean resolutionFlag) {
        setSymphonyId(symphonyId);
        setThirdPartyId(thirdPartyId);
        setCreator(creator);
        setText(text);
        if (lastModified != null) setLastModified(lastModified);
        setDescriptionFlag(descriptionFlag);
        setInternalFlag(internalFlag);
        setResolutionFlag(resolutionFlag);
    }

    public ConnectWiseComment(String symphonyId, String thirdPartyId, String creator, String text, Long lastModified) {
        setSymphonyId(symphonyId);
        setThirdPartyId(thirdPartyId);
        setCreator(creator);
        setText(text);
        if (lastModified != null) setLastModified(lastModified);
        setDescriptionFlag(false);
        setInternalFlag(true);
        setResolutionFlag(false);
    }


    //* ----------------------------- GETTERS / SETTERS ----------------------------- *//

    public String getSymphonyId() {
        return symphonyId;
    }

    public void setSymphonyId(String symphonyId) {
        this.symphonyId = symphonyId;
    }

    public String getThirdPartyId() {
        return thirdPartyId;
    }

    public void setThirdPartyId(String thirdPartyId) {
        this.thirdPartyId = thirdPartyId;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDescriptionFlag() {
        return descriptionFlag;
    }

    public void setDescriptionFlag(boolean descriptionFlag) {
        this.descriptionFlag = descriptionFlag;
    }

    public boolean isInternalFlag() {
        return internalFlag;
    }

    public void setInternalFlag(boolean internalFlag) {
        this.internalFlag = internalFlag;
    }

    public boolean isResolutionFlag() {
        return resolutionFlag;
    }

    public void setResolutionFlag(boolean resolutionFlag) {
        this.resolutionFlag = resolutionFlag;
    }

    public String toString() {
        return "Comment{" +
                "symphonyId='" + getSymphonyId() + "', " +
                "thirdPartyId='" + getThirdPartyId() + "', " +
                "creator='" + getCreator() + "', " +
                "text='" + getText() + "', " +
                "lastModified=" + lastModified +
                "}";
    }
}
