package org.opensingular.dbuserprovider;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.storage.UserStorageProvider;

public class DBUserStorageResource {

    @Context
    private KeycloakSession session;

    public DBUserStorageResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("users/{userId}/unlink")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unlinkUser(@PathParam("componentId") String componentId, @PathParam("userId") String userId) {
        RealmModel realm = session.getContext().getRealm();
        ComponentModel model = realm.getComponent(componentId);

        if (model == null) {
            ServicesLogger.LOGGER.debugf("User storage provider not found for componentId: %s", componentId);
            return Response.status(Status.NOT_FOUND).entity("User storage provider not found.").build();
        }

        if (!DBUserStorageProviderFactory.class.getName().equals(model.getProviderFactoryId()) &&
            !DBUserStorageProviderFactory.class.getSimpleName().equals(model.getProviderFactoryId())) {
             // Comparing with simple name as well due to some inconsistencies observed in older Keycloak versions or certain setups
            ServicesLogger.LOGGER.debugf("Component %s is not an instance of DBUserStorageProvider", componentId);
            return Response.status(Status.BAD_REQUEST).entity("Provider is not an instance of DBUserStorageProvider.").build();
        }
        
        // It's generally safer and more idiomatic in Keycloak to get the provider via the session if possible.
        // However, UserStorageProvider itself doesn't have provider-specific methods like unlinkUser.
        // We need the concrete type DBUserStorageProvider.
        // Keycloak's typical way to get a specific provider instance would be:
        // UserStorageProvider provider = session.getProvider(UserStorageProvider.class, model.getId());
        // But this returns the generic interface.
        // To call a custom method on our specific provider, we need to ensure we have an instance of that specific type.
        // One way is to re-fetch and configure as done in the factory, but this can be inefficient.
        // A common pattern for custom resources is to have the resource constructor take the provider instance,
        // which would be managed by a RealmResourceProviderFactory.
        // For now, let's try to get it via the factory's create method logic, which is not ideal from a resource.
        // A better approach would be if the factory that creates this JAX-RS resource also provides the specific provider instance.

        try {
            // Attempt to get the provider instance. This is a simplified way and might need refinement
            // depending on how Keycloak manages custom provider instances for JAX-RS resources.
            // The factory `create` method returns a new instance each time, which might not be what we want if it's stateful
            // or if there's an existing managed instance.
            // However, for UserStorageProvider, instances are often lightweight.
            
            DBUserStorageProviderFactory factory = (DBUserStorageProviderFactory) session.getKeycloakSessionFactory().getProviderFactory(UserStorageProviderFactory.class, model.getProviderId());
            if (factory == null) {
                 ServicesLogger.LOGGER.errorf("DBUserStorageProviderFactory not found for provider ID: %s", model.getProviderId());
                 return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Factory not found for provider.").build();
            }

            // The 'create' method in the factory typically creates a *new* instance.
            // If the provider holds state or is expensive to create, this is not ideal for just calling a method.
            // However, `DBUserStorageProvider` seems to be mostly stateless with configuration passed in.
            // A more direct way to get the *currently managed* instance isn't straightforward from a generic JAX-RS resource
            // without custom plumbing through the RealmResourceProviderFactory.
            // For now, we'll proceed with creating an instance via the factory, assuming it's acceptable.
            // This will use the component model `model` which has the ID `componentId`.
            UserStorageProvider genericProvider = session.getProvider(UserStorageProvider.class, componentId);
            
            if (genericProvider instanceof DBUserStorageProvider) {
                DBUserStorageProvider provider = (DBUserStorageProvider) genericProvider;
                provider.unlinkUser(realm, userId);
                return Response.ok().entity("{\"status\":\"User unlinked successfully\"}").build();
            } else {
                 ServicesLogger.LOGGER.errorf("Provider for componentId %s is not an instance of DBUserStorageProvider. Actual type: %s", componentId, genericProvider != null ? genericProvider.getClass().getName() : "null");
                 return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Could not obtain the correct provider instance.").build();
            }

        } catch (Exception e) {
            ServicesLogger.LOGGER.errorf(e, "Error unlinking user: %s", e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error unlinking user.").build();
        }
    }
}
