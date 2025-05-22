package org.opensingular.dbuserprovider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.UserCredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.cache.CachedUserModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.model.UserAdapter; // Will be used for dbUser
import org.opensingular.dbuserprovider.persistence.DataSourceProvider; // Not directly used, but for completeness
import org.opensingular.dbuserprovider.persistence.UserRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DBUserStorageProviderTests {

    @Mock
    private KeycloakSession session;
    @Mock
    private RealmModel realm;
    @Mock
    private ComponentModel model;
    @Mock
    private UserRepository repository; // Now injected
    @Mock
    private QueryConfigurations queryConfigurations;

    private DBUserStorageProvider provider;

    @Mock
    private UserProvider userProvider;
    @Mock
    private UserModel localUserModel; // A Keycloak local user
    @Mock
    private UserAdapter dbUserAdapter; // Represents a user from the external DB
    @Mock
    private UserCredentialModel userCredentialModel;
    @Mock
    private UserCredentialManager userCredentialManager;
    @Mock
    private CachedUserModel cachedUserModel; // For testing overwrite logic with cache

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_USER_ID_FROM_KEYCLOAK = "keycloak-user-id";
    private static final String TEST_USER_ID_FROM_DB = "db-user-id"; // External ID
    private static final String TEST_EMAIL = "testuser@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PROVIDER_ID = "test-provider-id";

    @BeforeEach
    void setUp() {
        // Setup common mock behaviors
        lenient().when(model.getId()).thenReturn(PROVIDER_ID);
        lenient().when(session.users()).thenReturn(userProvider);
        lenient().when(session.userCredentialManager()).thenReturn(userCredentialManager);

        // Instantiate the provider with the mocked repository and queryConfigurations
        provider = new DBUserStorageProvider(session, model, repository, queryConfigurations);

        // Setup for userCredentialModel if it's passed to methods
        lenient().when(userCredentialModel.getType()).thenReturn(PasswordCredentialModel.TYPE);
        lenient().when(userCredentialModel.getChallengeResponse()).thenReturn(TEST_PASSWORD);

        // Default behaviors for queryConfigurations (can be overridden in specific tests)
        lenient().when(queryConfigurations.getSyncNewUsersOnLogin()).thenReturn(true);
        lenient().when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(false);

        // Default behaviors for dbUserAdapter (representing user from DB)
        lenient().when(dbUserAdapter.getId()).thenReturn(TEST_USER_ID_FROM_DB); // External ID
        lenient().when(dbUserAdapter.getUsername()).thenReturn(TEST_USERNAME);
        lenient().when(dbUserAdapter.getEmail()).thenReturn(TEST_EMAIL);
        lenient().when(dbUserAdapter.getFirstName()).thenReturn("Test");
        lenient().when(dbUserAdapter.getLastName()).thenReturn("UserDB");
        Map<String, List<String>> dbAttrs = new HashMap<>();
        dbAttrs.put("db_attr", Collections.singletonList("db_value"));
        lenient().when(dbUserAdapter.getAttributes()).thenReturn(dbAttrs);


        // Default behaviors for localUserModel (representing user in Keycloak)
        lenient().when(localUserModel.getId()).thenReturn(TEST_USER_ID_FROM_KEYCLOAK);
        lenient().when(localUserModel.getUsername()).thenReturn(TEST_USERNAME);
        lenient().when(localUserModel.getEmail()).thenReturn(TEST_EMAIL);
         Map<String, List<String>> localAttrs = new HashMap<>();
        localAttrs.put("local_attr", Collections.singletonList("local_value"));
        lenient().when(localUserModel.getAttributes()).thenReturn(localAttrs);
    }

    // --- isValid() method tests ---

    @Test
    void isValid_newUser_syncTrue_createsAndLinksUser() {
        when(queryConfigurations.getSyncNewUsersOnLogin()).thenReturn(true);
        // User comes from external DB, not yet in Keycloak
        // The 'user' argument to isValid is typically a UserModel representing the external user data
        // For simplicity, let's use our dbUserAdapter as this 'user' argument.
        when(repository.validateCredentials(TEST_USERNAME, TEST_PASSWORD)).thenReturn(true);
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(null); // Not found locally
        when(userProvider.addUser(realm, TEST_USERNAME)).thenReturn(localUserModel); // Mock add user call

        boolean result = provider.isValid(realm, dbUserAdapter, userCredentialModel);

        assertTrue(result);
        verify(userProvider).addUser(realm, TEST_USERNAME);
        verify(localUserModel).setEnabled(true);
        verify(localUserModel).setEmail(dbUserAdapter.getEmail());
        verify(localUserModel).setFirstName(dbUserAdapter.getFirstName());
        verify(localUserModel).setLastName(dbUserAdapter.getLastName());
        verify(localUserModel).setFederationLink(PROVIDER_ID);
        verify(localUserModel).setAttribute(eq("db_attr"), anyList());
    }

    // logInvocations method removed

    @Test
    }


    @Test
    void isValid_newUser_syncFalse_doesNotCreateUser() {
        when(queryConfigurations.getSyncNewUsersOnLogin()).thenReturn(false);
        // 'user' argument to isValid is our dbUserAdapter
        when(repository.validateCredentials(TEST_USERNAME, TEST_PASSWORD)).thenReturn(true);
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(null); // Not found locally

        boolean result = provider.isValid(realm, dbUserAdapter, userCredentialModel);

        assertTrue(result); // Still true because credentials ARE valid against DB
        verify(userProvider, never()).addUser(any(), anyString());
    }

    @Test
    void isValid_existingFederatedUser_allowOverwriteTrue_updatesAttributes() {
        when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(true);
        // 'user' argument to isValid is localUserModel (or cachedUserModel wrapping it)
        when(repository.validateCredentials(TEST_USERNAME, TEST_PASSWORD)).thenReturn(true);
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn(PROVIDER_ID); // Federated with this provider

        // Simulate dbUserAdapter has different email and attributes than localUserModel
        when(dbUserAdapter.getEmail()).thenReturn(OTHER_EMAIL);
        Map<String, List<String>> newDbAttrs = new HashMap<>();
        newDbAttrs.put("db_attr_updated", Collections.singletonList("db_value_updated"));
        when(dbUserAdapter.getAttributes()).thenReturn(newDbAttrs);

        // To trigger the overwrite, the `dbUser` variable inside `isValid` must become the `dbUserAdapter`.
        // This happens if `user` is a `CachedUserModel` and `allowDatabaseToOverwriteKeycloak` is true.
        // And `this.getUserById(realm, user.getId())` returns our `dbUserAdapter`.
        // For this test, we'll assume `user` passed to `isValid` is `cachedUserModel`.
        when(cachedUserModel.getId()).thenReturn(TEST_USER_ID_FROM_KEYCLOAK);
        when(cachedUserModel.getUsername()).thenReturn(TEST_USERNAME);
        when(cachedUserModel.getEmail()).thenReturn(TEST_EMAIL); // Old email
        when(cachedUserModel.getCacheTimestamp()).thenReturn(System.currentTimeMillis() - 1000); // older than 500ms
        // This is the crucial part: make getUserById return the user from DB (dbUserAdapter)
        // The ID passed to getUserById will be the one from cachedUserModel, which is TEST_USER_ID_FROM_KEYCLOAK
        // But this ID should be the *external* ID if we expect to find the user in the external DB.
        // Let's use StorageId.externalId for this.
        String externalId = TEST_USER_ID_FROM_DB; // Assume this is the external part of localUserModel's ID
        // This setup is tricky. The `user.getId()` in `isValid` refers to Keycloak's internal ID.
        // `this.getUserById` is then called with this Keycloak ID.
        // Inside `getUserById`, `StorageId.externalId(id)` is used.
        // So, we need `localUserModel.getId()` to be `PROVIDER_ID + ":" + TEST_USER_ID_FROM_DB`
        // or ensure `StorageId.externalId(cachedUserModel.getId())` returns `TEST_USER_ID_FROM_DB`.

        // For simplicity, let's assume `dbUser` (the one from `repository.findUserBy...`) is used directly.
        // The code is: UserModel dbUser = user; then potentially dbUser = this.getUserById(...)
        // If 'user' is localUserModel, and no CachedUserModel logic path is taken, then 'dbUser' remains 'localUserModel'.
        // The attribute update logic: localUser.setEmail(dbUser.getEmail());
        // If dbUser is localUserModel, no change happens. This needs dbUser to be the external representation.

        // Let's simplify the test: assume the `dbUser` used for attribute comparison and setting
        // IS the `dbUserAdapter` (user from DB). This implies that the initial `user` parameter to `isValid`
        // effectively represents the state from the database for the purpose of attribute update.
        // The current code: `if (isValid) { UserModel localUser = session.users().getUserByUsername(realm, dbUser.getUsername()); ...}`
        // Here `dbUser.getUsername()` is from the input `user` (or `this.getUserById`).
        // Then `localUser.setEmail(dbUser.getEmail())` etc. are called.
        // So, if the input `user` to `isValid` is our `dbUserAdapter`, this test works.

        boolean result = provider.isValid(realm, dbUserAdapter, userCredentialModel); // Pass dbUserAdapter as the input 'user'

        assertTrue(result);
        verify(localUserModel).setEmail(OTHER_EMAIL); // Updated from dbUserAdapter
        verify(localUserModel).setFirstName(dbUserAdapter.getFirstName());
        verify(localUserModel).setLastName(dbUserAdapter.getLastName());
        // Check attributes. Existing local attributes should remain if not in dbUserAdapter.
        // New/updated attributes from dbUserAdapter should be set.
        verify(localUserModel).setAttribute(eq("db_attr_updated"), anyList());
    }

    @Test
    void isValid_existingFederatedUser_allowOverwriteFalse_doesNotUpdateAttributes() {
        when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(false);
        when(repository.validateCredentials(TEST_USERNAME, TEST_PASSWORD)).thenReturn(true);
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn(PROVIDER_ID);

        // dbUserAdapter has different email
        when(dbUserAdapter.getEmail()).thenReturn(OTHER_EMAIL);

        // Pass dbUserAdapter as the input 'user' that has been validated
        boolean result = provider.isValid(realm, dbUserAdapter, userCredentialModel);

        assertTrue(result);
        verify(localUserModel, never()).setEmail(OTHER_EMAIL);
        verify(localUserModel, never()).setFirstName(anyString()); // Assuming getFirstName was already called for setup
        verify(localUserModel, never()).setLastName(anyString());  // Same
        verify(localUserModel, never()).setAttribute(eq("db_attr_updated"), anyList());
    }

    // --- Lookup methods tests ---

    @Test
    void getUserByUsername_localUnlinkedUserExists_returnsLocal() {
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn(null); // UNLINKED

        UserModel result = provider.getUserByUsername(realm, TEST_USERNAME);

        assertSame(localUserModel, result);
        verify(repository, never()).findUserByUsername(anyString());
    }

    @Test
    void getUserByUsername_localUserFederatedWithThisProvider_proceedsToRepository() {
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn(PROVIDER_ID); // Federated with THIS provider
        when(repository.findUserByUsername(TEST_USERNAME)).thenReturn(Optional.of(new HashMap<>())); // Simulate DB user found

        UserModel result = provider.getUserByUsername(realm, TEST_USERNAME);

        assertNotNull(result);
        assertTrue(result instanceof UserAdapter); // Should be wrapped by UserAdapter
        verify(repository).findUserByUsername(TEST_USERNAME);
    }
    
    @Test
    void getUserByUsername_localUserFederatedWithOtherProvider_proceedsToRepository() {
        // This case is implicitly same as "localUserFederatedWithThisProvider" because the check is just localUser.getFederationLink() != null
        // The differentiation of *which* provider isn't made in getUserByUsername, but in isValid or other flows.
        // The key is that a federated local user (regardless of which provider) does NOT short-circuit the lookup.
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn("other-provider-id"); // Federated with OTHER provider
        when(repository.findUserByUsername(TEST_USERNAME)).thenReturn(Optional.of(new HashMap<>()));

        UserModel result = provider.getUserByUsername(realm, TEST_USERNAME);

        assertNotNull(result);
        assertTrue(result instanceof UserAdapter);
        verify(repository).findUserByUsername(TEST_USERNAME);
    }


    @Test
    void getUserByUsername_localUserNotFound_proceedsToRepository() {
        when(userProvider.getUserByUsername(realm, TEST_USERNAME)).thenReturn(null); // Local user NOT found
        when(repository.findUserByUsername(TEST_USERNAME)).thenReturn(Optional.of(new HashMap<>()));

        UserModel result = provider.getUserByUsername(realm, TEST_USERNAME);

        assertNotNull(result);
        assertTrue(result instanceof UserAdapter);
        verify(repository).findUserByUsername(TEST_USERNAME);
    }
    
    // Similar tests for getUserByEmail and getUserById can be added:
    // - getUserById_localUnlinkedUserExists_returnsLocal()
    // - getUserById_localUserFederated_proceedsToRepository() (Note: ID conversion logic StorageId.externalId)
    // - getUserById_localUserNotFound_proceedsToRepository()

    @Test
    void getUserById_localUnlinkedUserExists_returnsLocal() {
        when(userProvider.getUserById(realm, TEST_USER_ID_FROM_KEYCLOAK)).thenReturn(localUserModel);
        when(localUserModel.getFederationLink()).thenReturn(null); // UNLINKED

        UserModel result = provider.getUserById(realm, TEST_USER_ID_FROM_KEYCLOAK);

        assertSame(localUserModel, result);
        verify(repository, never()).findUserById(anyString());
    }


    // --- updateCredential() method tests ---

    @Test
    void updateCredential_unlinkedUser_delegatesToSessionManager() {
        when(localUserModel.getFederationLink()).thenReturn(null); // Unlinked
        // Ensure the input is of the correct type for the supportsCredentialType check
        lenient().when(userCredentialModel.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(session.userCredentialManager().updateCredential(realm, localUserModel, userCredentialModel)).thenReturn(true);


        boolean result = provider.updateCredential(realm, localUserModel, userCredentialModel);

        assertTrue(result);
        verify(session.userCredentialManager()).updateCredential(realm, localUserModel, userCredentialModel);
        verify(repository, never()).updateCredentials(anyString(), anyString());
    }

    @Test
    void updateCredential_federatedUser_callsRepository() {
        when(localUserModel.getFederationLink()).thenReturn(PROVIDER_ID); // Federated
        when(localUserModel.getUsername()).thenReturn(TEST_USERNAME);
        lenient().when(userCredentialModel.getType()).thenReturn(PasswordCredentialModel.TYPE);
        lenient().when(userCredentialModel.getChallengeResponse()).thenReturn(TEST_PASSWORD);
        when(repository.updateCredentials(TEST_USERNAME, TEST_PASSWORD)).thenReturn(true);

        boolean result = provider.updateCredential(realm, localUserModel, userCredentialModel);

        assertTrue(result);
        verify(repository).updateCredentials(TEST_USERNAME, TEST_PASSWORD);
        verify(session.userCredentialManager(), never()).updateCredential(any(), any(), any());
    }
}
