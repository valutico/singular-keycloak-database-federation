package org.opensingular.dbuserprovider;

import org.junit.Before;
import org.junit.Test;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.SynchronizationResult;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.UserRepository;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DBUserStorageProviderTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private RealmModel realm;

    @Mock
    private UserStorageProviderModel model;

    @Mock
    private DataSourceProvider dataSourceProvider;

    @Mock
    private QueryConfigurations queryConfigurations;

    @Mock
    private UserRepository userRepository;

    private DBUserStorageProvider provider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);
        // Use reflection to set the repository field
        try {
            java.lang.reflect.Field field = DBUserStorageProvider.class.getDeclaredField("repository");
            field.setAccessible(true);
            field.set(provider, userRepository);
        } catch (Exception e) {
            fail("Failed to set repository field: " + e.getMessage());
        }
    }

    @Test
    public void testSync() {
        when(queryConfigurations.isSyncEnabled()).thenReturn(true);
        when(userRepository.getAllUsers()).thenReturn(java.util.Collections.emptyList());

        SynchronizationResult result = provider.sync(session.getKeycloakSessionFactory(), realm.getId(), model);
        assertNotNull(result);
        assertEquals(0, result.getAdded());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getFailed());
    }

    @Test
    public void testSyncDisabled() {
        when(queryConfigurations.isSyncEnabled()).thenReturn(false);

        SynchronizationResult result = provider.sync(session.getKeycloakSessionFactory(), realm.getId(), model);
        assertNotNull(result);
        assertEquals(0, result.getAdded());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getFailed());
    }
    
    @Test
    public void testValidateCredentials() {
        String username = "testuser";
        String password = "testpass";
        
        UserModel user = mock(UserModel.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn("user123");

        CredentialInput input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(input.getChallengeResponse()).thenReturn(password);

        when(userRepository.validateCredentials(username, password)).thenReturn(true);
        when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(false);

        boolean isValid = provider.isValid(realm, user, input);
        assertTrue("Credentials should be valid", isValid);
        verify(userRepository).validateCredentials(username, password);
    }
}
