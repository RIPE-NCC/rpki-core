package net.ripe.rpki.rest.pojo;

public class CaStatus {
    private int announcementsValid;
    private int announcementsInvalid;
    private int announcementsUnknown;
    private int roaNumber;

    public int getAnnouncementsValid() {
        return announcementsValid;
    }

    public void setAnnouncementsValid(int announcementsValid) {
        this.announcementsValid = announcementsValid;
    }

    public int getAnnouncementsInvalid() {
        return announcementsInvalid;
    }

    public void setAnnouncementsInvalid(int announcementsInvalid) {
        this.announcementsInvalid = announcementsInvalid;
    }

    public int getRoaNumber() {
        return roaNumber;
    }

    public void setRoaNumber(int roaNumber) {
        this.roaNumber = roaNumber;
    }

    public int getAnnouncementsUnknown() {
        return announcementsUnknown;
    }

    public void setAnnouncementsUnknown(int announcementsUnknown) {
        this.announcementsUnknown = announcementsUnknown;
    }
}
