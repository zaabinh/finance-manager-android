package com.example.fintrack.app.core.di;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import androidx.lifecycle.ViewModel;

import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.home.viewmodel.DashboardViewModel;
import com.example.fintrack.app.user.repository.UserRepository;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

import org.junit.Test;

/** Runs on the JVM: nothing Android-backed (session storage, HTTP client) is built until a request is made. */
public class AppViewModelFactoryTest {
    private static final AppExecutors DIRECT = new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run);

    static final class UnknownViewModel extends ViewModel { }

    @Test
    public void containerSharesOneRepositoryAndFactoryBuildsANewViewModelPerRequest() {
        AppContainer container = new AppContainer(null, DIRECT);

        assertSame(container.authRepository(), container.authRepository());
        assertSame(container.userRepository(), container.userRepository());
        assertSame(container.dashboardRepository(), container.dashboardRepository());
        assertSame(DIRECT, container.executors());
        assertNotSame(container.viewModelFactory().create(AuthViewModel.class), container.viewModelFactory().create(AuthViewModel.class));
        assertNotSame(container.viewModelFactory().create(UserViewModel.class), container.viewModelFactory().create(UserViewModel.class));
        assertNotSame(container.viewModelFactory().create(DashboardViewModel.class), container.viewModelFactory().create(DashboardViewModel.class));
    }

    @Test
    public void factoryTakesRepositoriesFromTheContainerItWasGiven() {
        int[] requests = new int[2];
        AppContainer fakes = new AppContainer(null, DIRECT) {
            @Override public synchronized AuthRepository authRepository() { requests[0]++; return super.authRepository(); }
            @Override public synchronized UserRepository userRepository() { requests[1]++; return super.userRepository(); }
        };

        fakes.viewModelFactory().create(AuthViewModel.class);
        fakes.viewModelFactory().create(UserViewModel.class);

        assertEquals(1, requests[0]);
        assertEquals(2, requests[1]); // AuthViewModel loads the profile after login, UserViewModel uses it for everything else
    }

    @Test
    public void unknownViewModelIsRejected() {
        AppViewModelFactory factory = new AppContainer(null, DIRECT).viewModelFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(UnknownViewModel.class));
    }
}
