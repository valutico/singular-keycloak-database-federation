package org.opensingular.dbuserprovider.resource;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider; // Corrected import
import org.keycloak.services.resource.RealmResourceProviderFactory; // Corrected import

public class CustomRealmResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "singular-user-storage-unlink";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        // Passing null for ComponentModel as per refined instructions
        return new CustomRealmResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Nothing to initialize
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Nothing to do post-init
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
