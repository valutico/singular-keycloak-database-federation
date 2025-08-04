import org.mockito.Mockito;

public class TestUtils {
    public static <T> T mock(Class<T> classToMock) {
        return Mockito.mock(classToMock);
    }

    public static void verifyInteractions(Object mock) {
        Mockito.verify(mock);
    }

    public static void resetMocks(Object... mocks) {
        Mockito.reset(mocks);
    }
}