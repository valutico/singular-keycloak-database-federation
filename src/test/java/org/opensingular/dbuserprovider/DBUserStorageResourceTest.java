package org.opensingular.dbuserprovider;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DBUserStorageResourceTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private UserStorageProviderModel model;

    @Mock
    private UserStorageProvider provider;

    @Mock
    private KeycloakSessionFactory sessionFactory;

    private DBUserStorageResource resource;

    private static final String PROVIDER_ID = "singular-db-user-provider";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        resource = new DBUserStorageResource(session, model);
        when(session.getProvider(UserStorageProvider.class, PROVIDER_ID)).thenReturn(provider);
    }

    @Test
    public void testSyncWhenProviderNotFound() {
        when(session.getProvider(UserStorageProvider.class, model.getProviderId())).thenReturn(null);
        
        Response response = resource.sync();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWhenProviderDoesNotSupportSync() {
        // First, ensure the provider is returned
        when(model.getProviderId()).thenReturn(PROVIDER_ID);
        when(session.getProvider(UserStorageProvider.class, PROVIDER_ID)).thenReturn(provider);
        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        
        // We can't mock instanceof directly, so let's simulate the behavior by ensuring
        // the provider is not an instance of ImportSynchronization
        // The resource class will do instanceof check internally
        
        Response response = resource.sync();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncSuccess() {
        // Create a mock that explicitly implements both interfaces
        UserStorageProvider syncProvider = mock(DBUserStorageProvider.class);
        ImportSynchronization syncInterface = (ImportSynchronization) syncProvider;
        
        // Setup the model and session for sync
        when(model.getId()).thenReturn(PROVIDER_ID);
        when(model.getProviderId()).thenReturn(PROVIDER_ID);
        when(session.getProvider(UserStorageProvider.class, PROVIDER_ID)).thenReturn(syncProvider);
        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getId()).thenReturn("test-realm");
        
        // Create a properly initialized SynchronizationResult
        SynchronizationResult result = new SynchronizationResult();
        
        // Setup the sync method to return a result
        doReturn(result).when(syncInterface).sync(any(KeycloakSessionFactory.class), anyString(), any(UserStorageProviderModel.class));
        
        // Call the method under test
        Response response = resource.sync();
        
        // Verify the result
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(syncInterface).sync(eq(sessionFactory), eq("test-realm"), eq(model));
    }
}
