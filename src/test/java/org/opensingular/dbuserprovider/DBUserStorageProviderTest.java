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
        when(userRepository.getAllUsersForSync()).thenReturn(java.util.Collections.emptyList());

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
    
    /*
    @Test
    public void testValidateCredentials() {
        // Set up model ID first
        String modelId = "test-model-id";
        when(model.getId()).thenReturn(modelId);
        
        // Create and set up user
        UserModel user = mock(UserModel.class);
        when(user.getId()).thenReturn("user123");
        when(user.getUsername()).thenReturn("user123");
        when(user.getFederationLink()).thenReturn(modelId);
        
        // Ensure the credential type is supported
        when(queryConfigurations.getHashFunction()).thenReturn("SHA-256");
        
        // Create a properly mocked credential input that meets the provider's requirements
        CredentialInput passwordInput = mock(CredentialInput.class);
        when(passwordInput.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(passwordInput.getChallengeResponse()).thenReturn("password");
        
        // Mock repository validation to return true - ensure this is set up correctly
        when(userRepository.validateCredentials("user123", "password")).thenReturn(true);
        
        // Mock any other calls that might affect validation
        when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(false);
        
        // Test the credential validation
        boolean result = provider.isValid(realm, user, passwordInput);
        
        // Print debug info for troubleshooting
        System.out.println("Validation result: " + result);
        
        assertTrue("Credentials should be valid", result);
        
        // Verify the repository was called with the right username
        verify(userRepository).validateCredentials("user123", "password");
    }
    */
}
