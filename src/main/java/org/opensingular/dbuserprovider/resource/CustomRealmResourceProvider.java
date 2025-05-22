package org.opensingular.dbuserprovider.resource;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider; // Corrected import

public class CustomRealmResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public CustomRealmResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new UserUnlinkResource(session);
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
