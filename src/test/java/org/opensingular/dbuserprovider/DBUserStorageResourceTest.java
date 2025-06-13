package org.opensingular.dbuserprovider;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.component.ComponentModel;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

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
    private UserStorageProvider provider;

    @Mock
    private KeycloakSessionFactory sessionFactory;

    private DBUserStorageResource resource;
    private ComponentModel componentModel;

    private static final String PROVIDER_ID = "singular-db-user-provider";
    private static final String REALM_ID = "test-realm";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        
        // Create a proper ComponentModel instead of mocking it
        componentModel = new ComponentModel();
        componentModel.setId(PROVIDER_ID);
        componentModel.setName("Test Provider");
        componentModel.setProviderId(PROVIDER_ID);
        componentModel.setProviderType(UserStorageProvider.class.getName());
        componentModel.setConfig(new MultivaluedHashMap<>());
        
        // Set up the realm context
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getId()).thenReturn(REALM_ID);
        
        // Set up provider instance
        when(session.getProvider(eq(UserStorageProvider.class), any(UserStorageProviderModel.class))).thenReturn(provider);
        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        
        // Setup stream of components
        when(realm.getComponentsStream()).thenReturn(Stream.of(componentModel));
        
        // Create the resource
        resource = new DBUserStorageResource(session);
    }

    @Test
    public void testSyncWhenProviderNotFound() {
        // Return empty stream to simulate provider not found
        when(realm.getComponentsStream()).thenReturn(Stream.empty());
        
        Response response = resource.sync(PROVIDER_ID);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWhenProviderDoesNotSupportSync() {
        // Provider doesn't implement ImportSynchronization
        
        Response response = resource.sync(PROVIDER_ID);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncSuccess() {
        // Create a mock that implements both interfaces
        DBUserStorageProvider syncProvider = mock(DBUserStorageProvider.class);
        
        // Set up provider to return our mock
        when(session.getProvider(eq(UserStorageProvider.class), any(UserStorageProviderModel.class))).thenReturn(syncProvider);
        
        // Create a result object
        SynchronizationResult result = new SynchronizationResult();
        
        // Set up the sync method to return our result
        when(syncProvider.sync(any(KeycloakSessionFactory.class), eq(REALM_ID), any(UserStorageProviderModel.class)))
            .thenReturn(result);
        
        // Call the method under test
        Response response = resource.sync(PROVIDER_ID);
        
        // Verify the result
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(syncProvider).sync(eq(sessionFactory), eq(REALM_ID), any(UserStorageProviderModel.class));
    }

    @Test
    public void testSyncWithException() {
        // Create a mock that implements both interfaces
        DBUserStorageProvider syncProvider = mock(DBUserStorageProvider.class);
        
        // Set up provider to return our mock
        when(session.getProvider(eq(UserStorageProvider.class), any(UserStorageProviderModel.class))).thenReturn(syncProvider);
        
        // Set up the sync method to throw an exception
        when(syncProvider.sync(any(KeycloakSessionFactory.class), eq(REALM_ID), any(UserStorageProviderModel.class)))
            .thenThrow(new RuntimeException("Sync failed"));
        
        // Call the method under test
        Response response = resource.sync(PROVIDER_ID);
        
        // Verify the result
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWithNullRealm() {
        // Set up realm context to return null
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(null);
        
        // Call the method under test
        Response response = resource.sync(PROVIDER_ID);
        
        // Verify the result
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWithProviderInstanceNotFound() {
        // Set up provider to return null
        when(session.getProvider(eq(UserStorageProvider.class), any(UserStorageProviderModel.class))).thenReturn(null);
        
        // Call the method under test
        Response response = resource.sync(PROVIDER_ID);
        
        // Verify the result
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncProviderEndpoint() {
        // Create a mock that implements both interfaces
        DBUserStorageProvider syncProvider = mock(DBUserStorageProvider.class);
        
        // Set up provider to return our mock
        when(session.getProvider(eq(UserStorageProvider.class), any(UserStorageProviderModel.class))).thenReturn(syncProvider);
        
        // Create a result object
        SynchronizationResult result = new SynchronizationResult();
        
        // Set up the sync method to return our result
        when(syncProvider.sync(any(KeycloakSessionFactory.class), eq(REALM_ID), any(UserStorageProviderModel.class)))
            .thenReturn(result);
        
        // Call the syncProvider method directly
        Response response = resource.syncProvider(PROVIDER_ID);
        
        // Verify the result
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(syncProvider).sync(eq(sessionFactory), eq(REALM_ID), any(UserStorageProviderModel.class));
    }

    @Test
    public void testSyncWithEmptyProviderId() {
        // Call the method with empty provider ID
        Response response = resource.sync("");
        
        // Verify the result
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWithNullProviderId() {
        // Call the method with null provider ID
        Response response = resource.sync(null);
        
        // Verify the result
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }
}
