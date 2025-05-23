package org.opensingular.dbuserprovider;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

import com.google.auto.service.AutoService;

@AutoService(RealmResourceProviderFactory.class)
public class DBUserStorageResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String ID = "db-user-storage";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new DBUserStorageResource(session, null);
    }

    @Override
    public void init(Scope config) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }
}
