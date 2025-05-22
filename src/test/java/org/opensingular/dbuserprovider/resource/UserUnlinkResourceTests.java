package org.opensingular.dbuserprovider.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserUnlinkResourceTests {

    @Mock
    private KeycloakSession session;
    @Mock
    private KeycloakContext keycloakContext;
    @Mock
    private RealmModel realm;
    @Mock
    private UserProvider userProvider;
    @Mock
    private UserModel userModel;

    @InjectMocks
    private UserUnlinkResource userUnlinkResource; // This doesn't work as UserUnlinkResource is not a field of this test class.
                                                // We need to instantiate it manually.

    private static final String TEST_USER_ID = "test-user-id";
    private static final String TEST_REALM_NAME = "test-realm";
    private static final String FEDERATION_PROVIDER_ID = "test-provider-id";

    @BeforeEach
    void setUp() {
        userUnlinkResource = new UserUnlinkResource(session); // Manual instantiation

        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn(TEST_REALM_NAME);
        when(session.users()).thenReturn(userProvider);
    }

    @Test
    void unlinkUserFederation_success() {
        when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(userModel);
        when(userModel.getFederationLink()).thenReturn(FEDERATION_PROVIDER_ID); // User is federated

        Response response = userUnlinkResource.unlinkUserFederation(TEST_USER_ID);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(userModel).setFederationLink(null);
        // Verify log messages if possible/needed, though often this is implicit
    }

    @Test
    void unlinkUserFederation_userNotFound() {
        when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(null);

        Response response = userUnlinkResource.unlinkUserFederation(TEST_USER_ID);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(userModel, never()).setFederationLink(anyString()); // Or null
    }

    @Test
    void unlinkUserFederation_userAlreadyUnlinked() {
        when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(userModel);
        when(userModel.getFederationLink()).thenReturn(null); // User is already unlinked

        Response response = userUnlinkResource.unlinkUserFederation(TEST_USER_ID);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(userModel, never()).setFederationLink(anyString()); // Or null
    }
    
    @Test
    void unlinkUserFederation_exceptionDuringProcessing() {
        when(userProvider.getUserById(realm, TEST_USER_ID)).thenReturn(userModel);
        when(userModel.getFederationLink()).thenReturn(FEDERATION_PROVIDER_ID);
        doThrow(new RuntimeException("Unexpected error")).when(userModel).setFederationLink(null);

        Response response = userUnlinkResource.unlinkUserFederation(TEST_USER_ID);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        verify(userModel).setFederationLink(null); // Still called
    }
}
