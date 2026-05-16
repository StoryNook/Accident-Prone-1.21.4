package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class SpeakerRelationshipTest {

    private NannyData makeData(UUID owner, List<UUID> wards) {
        NannyData d = new NannyData();
        d.setOwnerUUID(owner);
        d.setWards(wards);
        return d;
    }

    @Test
    public void ownerUuidReturnsOwner() {
        UUID owner = UUID.randomUUID();
        NannyData d = makeData(owner, new ArrayList<>());
        assertEquals(SpeakerRelationship.OWNER, d.relationshipOf(owner));
    }

    @Test
    public void wardUuidReturnsLittle() {
        UUID owner = UUID.randomUUID();
        UUID ward = UUID.randomUUID();
        List<UUID> wards = new ArrayList<>();
        wards.add(ward);
        NannyData d = makeData(owner, wards);
        assertEquals(SpeakerRelationship.LITTLE, d.relationshipOf(ward));
    }

    @Test
    public void unrelatedUuidReturnsVisitor() {
        UUID owner = UUID.randomUUID();
        UUID ward = UUID.randomUUID();
        List<UUID> wards = new ArrayList<>();
        wards.add(ward);
        NannyData d = makeData(owner, wards);
        assertEquals(SpeakerRelationship.VISITOR, d.relationshipOf(UUID.randomUUID()));
    }

    @Test
    public void nullSpeakerReturnsVisitor() {
        NannyData d = makeData(UUID.randomUUID(), new ArrayList<>());
        assertEquals(SpeakerRelationship.VISITOR, d.relationshipOf(null));
    }

    @Test
    public void nullWardsListTreatedAsEmpty() {
        UUID owner = UUID.randomUUID();
        NannyData d = new NannyData();
        d.setOwnerUUID(owner);
        // Do not call setWards — leaves the field at its default.
        assertEquals(SpeakerRelationship.OWNER, d.relationshipOf(owner));
        assertEquals(SpeakerRelationship.VISITOR, d.relationshipOf(UUID.randomUUID()));
    }
}
