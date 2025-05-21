package org.opensingular.dbuserprovider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.storage.federated.UserFederatedStorageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.model.UserAdapter;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.RDBMS;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DBUserStorageProviderTest {

    @Mock
    private KeycloakSession session;
    @Mock
    private ComponentModel model;
    @Mock
    private DataSourceProvider dataSourceProvider;
    @Mock
    private QueryConfigurations queryConfigurations;
    @Mock
    private RealmModel realm;
    @Mock
    private UserProvider userLocalStorageProvider;
    @Mock
    private UserFederatedStorageProvider userFederatedStorageProvider;
    @Mock
    private javax.sql.DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private ResultSetMetaData resultSetMetaData;

    private DBUserStorageProvider provider; // Will be initialized in each test

    private final String testUsername = "testuser";
    private final String testUserEmail = "testuser@example.com";
    private final String testUserFirstName = "Test";
    private final String testUserLastName = "User";
    private final String testUserId = "db-user-id-123";

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(model.getId()).thenReturn("test-component-id");
        lenient().when(session.userLocalStorage()).thenReturn(userLocalStorageProvider);
        lenient().when(session.userFederatedStorage()).thenReturn(userFederatedStorageProvider);
        lenient().when(userFederatedStorageProvider.getAttributes(any(RealmModel.class), anyString())).thenReturn(new MultivaluedHashMap<>());

        // Default QueryConfigurations, can be overridden in tests BEFORE provider instantiation
        lenient().when(queryConfigurations.getFindByUsername()).thenReturn("SELECT * FROM users WHERE username = ?");
        lenient().when(queryConfigurations.getRDBMS()).thenReturn(RDBMS.POSTGRESQL);
        lenient().when(queryConfigurations.getAllowDatabaseToOverwriteKeycloak()).thenReturn(false);
        // Default sync/unlink to false, tests must explicitly enable them
        lenient().when(queryConfigurations.isSyncEnabled()).thenReturn(false);
        lenient().when(queryConfigurations.isUnlinkEnabled()).thenReturn(false);


        // Mock DataSourceProvider and JDBC chain (common for all tests)
        lenient().when(dataSourceProvider.getDataSource()).thenReturn(Optional.of(dataSource));
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        lenient().when(resultSet.getMetaData()).thenReturn(resultSetMetaData);

        // Provider is NOT instantiated here anymore. Each test will do it.
    }

    private void mockDbUserFound(Map<String, String> userData) throws SQLException {
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSetMetaData.getColumnCount()).thenReturn(userData.size());

        int i = 1;
        for (Map.Entry<String, String> entry : userData.entrySet()) {
            lenient().when(resultSetMetaData.getColumnLabel(i)).thenReturn(entry.getKey());
            lenient().when(resultSet.getString(i)).thenReturn(entry.getValue());
            lenient().when(resultSet.getString(entry.getKey())).thenReturn(entry.getValue());
            i++;
        }
    }

    private void mockDbUserNotFound() throws SQLException {
        when(resultSet.next()).thenReturn(false);
    }

    @Test
    @DisplayName("Sync new user when sync is enabled")
    void testSyncNewUser_SyncEnabled() throws SQLException {
        when(queryConfigurations.isSyncEnabled()).thenReturn(true);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);


        Map<String, String> dbUserData = new HashMap<>();
        dbUserData.put("id", testUserId);
        dbUserData.put("username", testUsername);
        dbUserData.put("email", testUserEmail);
        dbUserData.put("firstName", testUserFirstName);
        dbUserData.put("lastName", testUserLastName);
        mockDbUserFound(dbUserData);

        when(userLocalStorageProvider.getUserByUsername(realm, testUsername)).thenReturn(null);
        UserModel newLocalUser = mock(UserModel.class);
        when(userLocalStorageProvider.addUser(realm, testUsername)).thenReturn(newLocalUser);

        UserModel result = provider.getUserByUsername(testUsername, realm);

        assertNotNull(result);
        assertEquals(newLocalUser, result);
        verify(userLocalStorageProvider).addUser(realm, testUsername);
        verify(newLocalUser).setFirstName(testUserFirstName);
        verify(newLocalUser).setLastName(testUserLastName);
        verify(newLocalUser).setEmail(testUserEmail);

        ArgumentCaptor<String> federationLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(newLocalUser).setFederationLink(federationLinkCaptor.capture());
        assertEquals("f:" + model.getId() + ":" + testUserId, federationLinkCaptor.getValue());
    }

    @Test
    @DisplayName("Sync existing user when sync is enabled")
    void testSyncExistingUser_SyncEnabled() throws SQLException {
        when(queryConfigurations.isSyncEnabled()).thenReturn(true);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);

        String updatedEmail = "updatedemail@example.com";

        Map<String, String> dbUserData = new HashMap<>();
        dbUserData.put("id", testUserId);
        dbUserData.put("username", testUsername);
        dbUserData.put("email", updatedEmail);
        dbUserData.put("firstName", testUserFirstName);
        dbUserData.put("lastName", testUserLastName);
        mockDbUserFound(dbUserData);

        UserModel existingLocalUser = mock(UserModel.class);
        // when(existingLocalUser.getUsername()).thenReturn(testUsername); // Removed unnecessary stubbing
        when(userLocalStorageProvider.getUserByUsername(realm, testUsername)).thenReturn(existingLocalUser);

        UserModel result = provider.getUserByUsername(testUsername, realm);

        assertNotNull(result);
        assertEquals(existingLocalUser, result);
        verify(userLocalStorageProvider, never()).addUser(any(RealmModel.class), anyString());
        verify(existingLocalUser).setEmail(updatedEmail);
        verify(existingLocalUser).setFirstName(testUserFirstName);
        verify(existingLocalUser).setLastName(testUserLastName);
    }

    @Test
    @DisplayName("Sync disabled, user found in DB")
    void testSyncDisabled_UserFoundInDB() throws SQLException {
        when(queryConfigurations.isSyncEnabled()).thenReturn(false); // Explicitly false
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);

        Map<String, String> dbUserData = new HashMap<>();
        dbUserData.put("id", testUserId);
        dbUserData.put("username", testUsername);
        dbUserData.put("email", testUserEmail);
        mockDbUserFound(dbUserData);

        UserModel result = provider.getUserByUsername(testUsername, realm);

        assertNotNull(result);
        assertTrue(result instanceof UserAdapter, "Result should be a UserAdapter instance");
        assertEquals(testUsername, result.getUsername());
        assertEquals(testUserEmail, result.getEmail());

        verify(userLocalStorageProvider, never()).getUserByUsername(any(RealmModel.class), anyString());
        verify(userLocalStorageProvider, never()).addUser(any(RealmModel.class), anyString());
    }


    @Test
    @DisplayName("User not found in external DB")
    void testUserNotFoundInDB() throws SQLException {
        when(queryConfigurations.isSyncEnabled()).thenReturn(true); // Sync status shouldn't matter if user not in DB
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);
        mockDbUserNotFound();

        UserModel result = provider.getUserByUsername(testUsername, realm);

        assertNull(result);
        verify(userLocalStorageProvider, never()).getUserByUsername(any(RealmModel.class), anyString());
        verify(userLocalStorageProvider, never()).addUser(any(RealmModel.class), anyString());
    }


    @Test
    @DisplayName("Unlink user when unlinking is enabled")
    void testUnlinkUser_UnlinkEnabled() {
        when(queryConfigurations.isUnlinkEnabled()).thenReturn(true);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);
        UserModel mockUser = mock(UserModel.class);
        when(mockUser.getFederationLink()).thenReturn("some-link"); // User is currently linked

        boolean result = provider.unlinkUser(realm, mockUser);

        assertTrue(result);
        verify(mockUser).setFederationLink(null);
    }

    @Test
    @DisplayName("Unlink user when unlinking is disabled")
    void testUnlinkUser_UnlinkDisabled() {
        when(queryConfigurations.isUnlinkEnabled()).thenReturn(false);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);
        UserModel mockUser = mock(UserModel.class);
        // No need to mock getFederationLink as it shouldn't be called if unlinking is disabled early.

        boolean result = provider.unlinkUser(realm, mockUser);

        assertFalse(result);
        verify(mockUser, never()).setFederationLink(any());
    }

    @Test
    @DisplayName("Unlink user who is already unlinked")
    void testUnlinkUser_AlreadyUnlinked() {
        when(queryConfigurations.isUnlinkEnabled()).thenReturn(true);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);
        UserModel mockUser = mock(UserModel.class);
        when(mockUser.getFederationLink()).thenReturn(null); // User is already unlinked

        boolean result = provider.unlinkUser(realm, mockUser);

        assertTrue(result); // Should be true as the desired state (unlinked) is met
        verify(mockUser, never()).setFederationLink(any());
    }
    
    @Test
    @DisplayName("Sync new user with multiple attributes when sync is enabled")
    void testSyncNewUser_MultipleAttributes_SyncEnabled() throws SQLException {
        when(queryConfigurations.isSyncEnabled()).thenReturn(true);
        provider = new DBUserStorageProvider(session, model, dataSourceProvider, queryConfigurations);

        Map<String, String> dbUserData = new HashMap<>();
        dbUserData.put("id", testUserId);
        dbUserData.put("username", testUsername);
        dbUserData.put("email", testUserEmail);
        dbUserData.put("firstName", testUserFirstName);
        dbUserData.put("lastName", testUserLastName);
        dbUserData.put("phoneNumber", "1234567890");
        dbUserData.put("customAttribute", "customValue");
        mockDbUserFound(dbUserData);

        when(userLocalStorageProvider.getUserByUsername(realm, testUsername)).thenReturn(null); // No local user
        UserModel newLocalUser = mock(UserModel.class);
        // Mock the setSingleAttribute method for the new user
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);


        when(userLocalStorageProvider.addUser(realm, testUsername)).thenReturn(newLocalUser);
        // Capture setAttribute calls
        Map<String, List<String>> attributesToSet = new HashMap<>();
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            List<String> value = invocation.getArgument(1);
            attributesToSet.put(key, value);
            return null;
        }).when(newLocalUser).setAttribute(anyString(), anyList());


        UserModel result = provider.getUserByUsername(testUsername, realm);

        assertNotNull(result);
        assertEquals(newLocalUser, result);
        verify(userLocalStorageProvider).addUser(realm, testUsername);
        verify(newLocalUser).setFirstName(testUserFirstName);
        verify(newLocalUser).setLastName(testUserLastName);
        verify(newLocalUser).setEmail(testUserEmail);
        
        // Verify all attributes from dbUserData were attempted to be set
        // Note: UserAdapter logic internally calls setAttribute for each item in the map.
        // We are verifying the effect on the *local* user model.
        // The syncUser method copies attributes from the UserAdapter.
        
        verify(newLocalUser).setAttribute("phoneNumber", Collections.singletonList("1234567890"));
        verify(newLocalUser).setAttribute("customAttribute", Collections.singletonList("customValue"));
        // Also verify specific attributes like username, id are set if they are part of the general attribute handling
        verify(newLocalUser).setAttribute("username", Collections.singletonList(testUsername));
        verify(newLocalUser).setAttribute("id", Collections.singletonList(testUserId));

    }
}
