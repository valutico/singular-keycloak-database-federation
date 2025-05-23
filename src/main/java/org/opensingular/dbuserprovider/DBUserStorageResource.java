package org.opensingular.dbuserprovider;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.ApplianceBootstrap;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@JBossLog
public class DBUserStorageResource implements RealmResourceProvider {
    private final KeycloakSession session;
    private final UserStorageProviderModel model;

    public DBUserStorageResource(KeycloakSession session, UserStorageProviderModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
        // No resources to clean up
    }

    @POST
    @Path("sync")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sync() {
        UserStorageProvider provider = session.getProvider(UserStorageProvider.class, model.getProviderId());
        if (provider == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        if (!(provider instanceof ImportSynchronization)) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        ImportSynchronization sync = (ImportSynchronization) provider;
        SynchronizationResult result = sync.sync(session.getKeycloakSessionFactory(), session.getContext().getRealm().getId(), model);
        return Response.ok(result).build();
    }
}
