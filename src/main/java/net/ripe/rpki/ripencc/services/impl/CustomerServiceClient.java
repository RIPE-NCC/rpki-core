package net.ripe.rpki.ripencc.services.impl;

import net.ripe.rpki.commons.util.EqualsSupport;

import java.util.List;

public interface CustomerServiceClient {
    boolean isAvailable();

    List<MemberSummary> findAllMemberSummaries();

    class MemberSummary extends EqualsSupport {
        private long membershipId;
        private String regId;
        private String organisationName;

        MemberSummary() {
        }

        public MemberSummary(long membershipId, String regid, String organisationName) {
            this.membershipId = membershipId;
            this.regId = regid;
            this.organisationName = organisationName;
        }

        public long getMembershipId() {
            return membershipId;
        }

        public String getRegId() {
            return regId;
        }

        public String getOrganisationName() {
            return organisationName;
        }
    }
}
