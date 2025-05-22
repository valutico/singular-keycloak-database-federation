package org.opensingular.dbuserprovider;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserProvider;
import org.keycloak.services.ServicesLogger;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DBUserStorageResourceTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realmModel;

    @Mock
    private ComponentModel componentModel;

    @Mock
    private DBUserStorageProvider dbUserStorageProvider;
    
    @Mock
    private UserStorageProvider generalUserStorageProvider;


    @Mock
    private UriInfo uriInfo;
    
    @Mock
    private org.keycloak.models.KeycloakContext keycloakContext;
    
    @Mock
    private KeycloakSessionFactory keycloakSessionFactory;
    
    @Mock
    private DBUserStorageProviderFactory dbUserStorageProviderFactory;


    private DBUserStorageResource resource;

    private final String componentId = "test-component-id";
    private final String userId = "test-user-id";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        resource = new DBUserStorageResource(session);

        when(session.getContext()).thenReturn(keycloakContext);
        when(keycloakContext.getRealm()).thenReturn(realmModel);
    }

    @Test
    public void testUnlinkUserEndpoint_success() {
        when(realmModel.getComponent(componentId)).thenReturn(componentModel);
        when(componentModel.getProviderFactoryId()).thenReturn(DBUserStorageProviderFactory.class.getName());
        when(componentModel.getProviderId()).thenReturn(DBUserStorageProviderFactory.ID); // Assuming providerId is the factory ID
        when(session.getProvider(UserStorageProvider.class, componentId)).thenReturn(dbUserStorageProvider);
        // Ensure keycloakSessionFactory is available if factory.create is called, though it might not be with getProvider
        when(session.getKeycloakSessionFactory()).thenReturn(keycloakSessionFactory);
        when(keycloakSessionFactory.getProviderFactory(UserStorageProviderFactory.class, DBUserStorageProviderFactory.ID)).thenReturn(dbUserStorageProviderFactory);


        Response response = resource.unlinkUser(componentId, userId);

        verify(dbUserStorageProvider).unlinkUser(realmModel, userId);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testUnlinkUserEndpoint_componentNotFound() {
        when(realmModel.getComponent(componentId)).thenReturn(null);

        Response response = resource.unlinkUser(componentId, userId);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(dbUserStorageProvider, never()).unlinkUser(any(RealmModel.class), anyString());
    }

    @Test
    public void testUnlinkUserEndpoint_incorrectProviderType_byFactoryIdName() {
        when(realmModel.getComponent(componentId)).thenReturn(componentModel);
        when(componentModel.getProviderFactoryId()).thenReturn("some-other-factory-id");
        // No need to mock session.getProvider as it won't be reached if factory ID check fails first

        Response response = resource.unlinkUser(componentId, userId);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(dbUserStorageProvider, never()).unlinkUser(any(RealmModel.class), anyString());
    }
    
    @Test
    public void testUnlinkUserEndpoint_incorrectProviderType_byFactoryIdSimpleName() {
        when(realmModel.getComponent(componentId)).thenReturn(componentModel);
        // Mocking factory ID to be something that matches neither full name nor simple name of DBUserStorageProviderFactory
        when(componentModel.getProviderFactoryId()).thenReturn("AnotherDifferentFactory");
        // No need to mock session.getProvider as it won't be reached if factory ID check fails first

        Response response = resource.unlinkUser(componentId, userId);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(dbUserStorageProvider, never()).unlinkUser(any(RealmModel.class), anyString());
    }


    @Test
    public void testUnlinkUserEndpoint_providerNotDBInstance() {
        when(realmModel.getComponent(componentId)).thenReturn(componentModel);
        when(componentModel.getProviderFactoryId()).thenReturn(DBUserStorageProviderFactory.class.getName());
        when(componentModel.getProviderId()).thenReturn(DBUserStorageProviderFactory.ID);
        // Return a generic UserStorageProvider that is not an instance of DBUserStorageProvider
        when(session.getProvider(UserStorageProvider.class, componentId)).thenReturn(generalUserStorageProvider);
        when(session.getKeycloakSessionFactory()).thenReturn(keycloakSessionFactory);
        when(keycloakSessionFactory.getProviderFactory(UserStorageProviderFactory.class, DBUserStorageProviderFactory.ID)).thenReturn(dbUserStorageProviderFactory);


        Response response = resource.unlinkUser(componentId, userId);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        // The error message "Could not obtain the correct provider instance." is expected
        assertTrue(response.getEntity().toString().contains("Could not obtain the correct provider instance."));
        verify(dbUserStorageProvider, never()).unlinkUser(any(RealmModel.class), anyString());
    }


    @Test
    public void testUnlinkUserEndpoint_providerThrowsException() {
        when(realmModel.getComponent(componentId)).thenReturn(componentModel);
        when(componentModel.getProviderFactoryId()).thenReturn(DBUserStorageProviderFactory.class.getName());
        when(componentModel.getProviderId()).thenReturn(DBUserStorageProviderFactory.ID);
        when(session.getProvider(UserStorageProvider.class, componentId)).thenReturn(dbUserStorageProvider);
        doThrow(new RuntimeException("DB error")).when(dbUserStorageProvider).unlinkUser(realmModel, userId);
        when(session.getKeycloakSessionFactory()).thenReturn(keycloakSessionFactory);
        when(keycloakSessionFactory.getProviderFactory(UserStorageProviderFactory.class, DBUserStorageProviderFactory.ID)).thenReturn(dbUserStorageProviderFactory);


        Response response = resource.unlinkUser(componentId, userId);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        verify(dbUserStorageProvider).unlinkUser(realmModel, userId);
    }
}
