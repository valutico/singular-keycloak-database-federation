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

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(session.getProvider(UserStorageProvider.class, model.getProviderId())).thenReturn(provider);
        when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        resource = new DBUserStorageResource(session, model);
    }

    @Test
    public void testSyncWhenProviderNotFound() {
        when(session.getProvider(UserStorageProvider.class, model.getProviderId())).thenReturn(null);
        
        Response response = resource.sync();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncWhenProviderDoesNotSupportSync() {
        UserStorageProviderFactory regularFactory = mock(UserStorageProviderFactory.class);
        when(sessionFactory.getProviderFactory(UserStorageProvider.class, model.getProviderId())).thenReturn(regularFactory);
        
        Response response = resource.sync();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSyncSuccess() {
        // Create a factory that implements both interfaces
        UserStorageProviderFactory syncFactory = mock(UserStorageProviderFactory.class, withSettings().extraInterfaces(ImportSynchronization.class));
        when(sessionFactory.getProviderFactory(UserStorageProvider.class, model.getProviderId())).thenReturn(syncFactory);
        
        // Cast to ImportSynchronization to stub the sync method
        ImportSynchronization syncCapable = (ImportSynchronization) syncFactory;
        SynchronizationResult mockResult = mock(SynchronizationResult.class);
        when(syncCapable.sync(any(), anyString(), any())).thenReturn(mockResult);
        
        Response response = resource.sync();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(syncCapable).sync(any(), anyString(), any());
    }
}
