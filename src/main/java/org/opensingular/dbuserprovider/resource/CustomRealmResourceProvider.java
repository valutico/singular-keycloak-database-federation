package org.opensingular.dbuserprovider.resource;

import org.keycloak.models.KeycloakSession;
// ComponentModel import removed
import org.keycloak.services.resources.RealmResourceProvider;

public class CustomRealmResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;
    // ComponentModel model; removed

    public CustomRealmResourceProvider(KeycloakSession session /*, ComponentModel model removed */) {
        this.session = session;
        // this.model = model; removed
    }

    @Override
    public Object getResource() {
        // Pass only session to UserUnlinkResource, as ComponentModel is removed
        return new UserUnlinkResource(session);
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
