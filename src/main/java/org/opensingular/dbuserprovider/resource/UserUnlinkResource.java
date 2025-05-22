package org.opensingular.dbuserprovider.resource;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@JBossLog
public class UserUnlinkResource {

    private final KeycloakSession session;

    public UserUnlinkResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("/{userId}/unlink-federation") // This path is relative to CustomRealmResourceProvider
    @Produces(MediaType.APPLICATION_JSON)
    public Response unlinkUserFederation(@PathParam("userId") String userId) {
        RealmModel realm = session.getContext().getRealm();
        log.infov("Attempting to unlink user {0} in realm {1}", userId, realm.getName());

        try {
            UserModel userModel = session.users().getUserById(realm, userId);

            if (userModel == null) {
                log.warnf("User not found: {0}", userId);
                return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"User not found\"}").build();
            }

            if (userModel.getFederationLink() == null) {
                log.warnf("User {0} is not federated or already unlinked.", userId);
                return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"User is not federated or already unlinked\"}").build();
            }
            
            // ComponentModel check removed as per latest instructions
            // if (this.componentModel != null && !this.componentModel.getId().equals(userModel.getFederationLink())) {
            //     log.warnf("User {0} is federated by a different provider ({1}) than this one ({2})", userId, userModel.getFederationLink(), this.componentModel.getId());
            //     return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"User is federated by a different provider.\"}").build();
            // }
            
            userModel.setFederationLink(null);
            log.infov("Successfully unlinked user {0} from federation.", userId); // Removed provider ID from log
            return Response.ok().entity("{\"message\": \"User unlinked successfully.\"}").build();

        } catch (Exception e) {
            log.errorf(e, "Error unlinking user {0}", userId);
            return Response.serverError().entity("{\"error\": \"An unexpected error occurred: " + e.getMessage() + "\"}").build();
        }
    }
}
