package org.opensingular.dbuserprovider;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.component.ComponentModel;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@JBossLog
public class DBUserStorageResource implements RealmResourceProvider {
    private final KeycloakSession session;

    public DBUserStorageResource(KeycloakSession session) {
        this.session = session;
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
    @Path("providers/{providerId}/sync")
    @Produces(MediaType.APPLICATION_JSON)
    public Response syncProvider(@PathParam("providerId") String providerId) {
        log.infov("Sync requested for provider ID: {0}", providerId);
        
        // Get the realm
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            return Response.status(Status.BAD_REQUEST).entity("Realm not found").build();
        }

        // Get the provider model
        ComponentModel componentModel = realm.getComponentsStream()
            .filter(component -> component.getId().equals(providerId) && 
                   component.getProviderType().equals(UserStorageProvider.class.getName()))
            .findFirst()
            .orElse(null);
            
        if (componentModel == null) {
            log.warnv("Provider not found with ID: {0}", providerId);
            return Response.status(Status.NOT_FOUND).entity("Provider not found").build();
        }

        try {
            // Convert to UserStorageProviderModel safely
            UserStorageProviderModel model = new UserStorageProviderModel(componentModel);
            
            // Get the provider instance
            UserStorageProvider provider = session.getProvider(UserStorageProvider.class, model.getProviderId());
            if (provider == null) {
                log.warnv("Provider instance not available for ID: {0}", providerId);
                return Response.status(Status.NOT_FOUND).entity("Provider instance not found").build();
            }
    
            // Check if provider supports synchronization
            if (!(provider instanceof ImportSynchronization)) {
                log.warnv("Provider does not support synchronization: {0}", providerId);
                return Response.status(Status.BAD_REQUEST).entity("Provider does not support synchronization").build();
            }
    
            // Execute the synchronization
            ImportSynchronization sync = (ImportSynchronization) provider;
            SynchronizationResult result = sync.sync(session.getKeycloakSessionFactory(), realm.getId(), model);
            log.infov("Synchronization completed for provider {0}: {1} added, {2} updated, {3} failed", 
                    providerId, result.getAdded(), result.getUpdated(), result.getFailed());
            return Response.ok(result).build();
        } catch (Exception e) {
            log.errorv(e, "Error during synchronization for provider {0}", providerId);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Synchronization error: " + e.getMessage())
                    .build();
        }
    }

    // For backward compatibility
    @POST
    @Path("sync")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sync(@QueryParam("providerId") String providerId) {
        if (providerId == null || providerId.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).entity("providerId parameter is required").build();
        }
        return syncProvider(providerId);
    }
}
