package com.storynook.nanny.membership;

import com.storynook.nanny.MembershipProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompositeMembershipProvider implements MembershipProvider {

    private final List<MembershipProvider> providers = new ArrayList<MembershipProvider>();

    public void add(MembershipProvider p) { if (p != null) providers.add(p); }
    public boolean isEmpty() { return providers.isEmpty(); }
    public List<MembershipProvider> getProviders() { return providers; }

    @Override
    public boolean isUnlocked(UUID playerUUID) {
        for (MembershipProvider p : providers) {
            if (p.isUnlocked(playerUUID)) return true;
        }
        return false;
    }

    @Override
    public void refresh(UUID playerUUID) {
        for (MembershipProvider p : providers) p.refresh(playerUUID);
    }
}
