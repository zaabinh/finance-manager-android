package com.example.fintrack.app.auth.viewmodel;

import com.example.fintrack.app.core.network.RequestHandle;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.repository.UserRepository;

/**
 * ViewModel quản lý logic liên quan đến xác thực (Auth),
 * làm cầu nối giữa UI (Activity/Fragment) và Repository.
 * Mọi thay đổi phiên đăng nhập (đăng nhập, đăng xuất) được ghi tại đây qua SessionStore; Activity chỉ đọc và điều hướng.
 */
public class AuthViewModel extends ViewModel {

    /** Màn hình cần mở sau khi đăng nhập xong (theo onboardingCompleted của server). */
    public enum LoginDestination { HOME, SETUP }

    // Repository chịu trách nhiệm gọi API hoặc lấy dữ liệu từ local
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final SessionStore sessionStore;

    // Các LiveData lưu trữ trạng thái kết quả của các chức năng (Loading, Success, Error)
    private final MutableLiveData<Resource<LoginResponse>> loginResult = new MutableLiveData<>();
    private final MutableLiveData<Event<LoginDestination>> loginNavigation = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> registerResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> verifyEmailResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Resource<Void>>> resendVerificationResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Resource<Void>>> forgotPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resetPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Resource<Void>>> logoutResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Resource<TokenResponse>>> changePasswordResult = new MutableLiveData<>();

    // Biến tạm để lưu trữ thông tin email và mã OTP giữa các màn hình
    private String registeredEmail = "";
    private String forgotPasswordEmail = "";
    private String resetOtp = "";

    // Lần đăng nhập mới nhất; phản hồi của các lần cũ hơn bị bỏ qua. Chỉ dùng trên main thread.
    private int loginAttempt;
    private RequestHandle loginProfile = RequestHandle.NONE;
    // Phiên vừa tạo bởi lần đăng nhập đang chờ tải hồ sơ; null khi không có.
    private SessionSnapshot loginSession;

    public AuthViewModel(AuthRepository authRepository, UserRepository userRepository, SessionStore sessionStore) {
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
    }

    // --- Các hàm Getter & Setter để lưu và lấy dữ liệu tạm ---

    public String getRegisteredEmail() {
        return registeredEmail;
    }

    public void setRegisteredEmail(String registeredEmail) {
        this.registeredEmail = registeredEmail;
    }

    public String getForgotPasswordEmail() {
        return forgotPasswordEmail;
    }

    public String getResetOtp() {
        return resetOtp;
    }

    public void setResetOtp(String resetOtp) {
        this.resetOtp = resetOtp;
    }

    // --- Các hàm Getter trả về LiveData để UI quan sát (observe) thay đổi ---

    /** Trạng thái của yêu cầu đăng nhập (chỉ để hiển thị); việc lưu phiên do ViewModel thực hiện. */
    public LiveData<Resource<LoginResponse>> getLoginResult() {
        return loginResult;
    }

    /** Sự kiện một lần: đăng nhập và tải hồ sơ đã xong, phiên đã được lưu. */
    public LiveData<Event<LoginDestination>> getLoginNavigation() {
        return loginNavigation;
    }

    public LiveData<Resource<Void>> getRegisterResult() {
        return registerResult;
    }

    public LiveData<Resource<Void>> getVerifyEmailResult() {
        return verifyEmailResult;
    }

    /** Sự kiện một lần (LOADING, rồi SUCCESS/ERROR): màn hình tạo lại không hiện lại toast hay khởi động lại bộ đếm. */
    public LiveData<Event<Resource<Void>>> getResendVerificationResult() {
        return resendVerificationResult;
    }

    /** Sự kiện một lần, như {@link #getResendVerificationResult()}. */
    public LiveData<Event<Resource<Void>>> getForgotPasswordResult() {
        return forgotPasswordResult;
    }

    public LiveData<Resource<Void>> getResetPasswordResult() {
        return resetPasswordResult;
    }

    /** Sự kiện một lần: yêu cầu đăng xuất đã kết thúc (SUCCESS hoặc ERROR) và phiên local của nó đã được xóa. */
    public LiveData<Event<Resource<Void>>> getLogoutResult() {
        return logoutResult;
    }

    /** Sự kiện một lần: kết quả đổi mật khẩu khi đang đăng nhập. */
    public LiveData<Event<Resource<TokenResponse>>> getChangePasswordResult() {
        return changePasswordResult;
    }

    // --- Các hàm xử lý nghiệp vụ xác thực ---

    /**
     * Thực hiện đăng nhập: lưu token thành một phiên mới, tải hồ sơ của phiên đó rồi phát sự kiện điều hướng.
     * Lần gọi mới thay thế lần đang chạy: phản hồi của lần cũ không ghi phiên và không điều hướng.
     * rememberMe ("Ghi nhớ tôi") được gửi cho server và lưu cùng phiên: false thì phiên kết thúc khi đóng ứng dụng.
     */
    public void login(String usernameOrEmail, String password, boolean rememberMe) {
        int attempt = ++loginAttempt;
        discardLoginSession();
        loginResult.setValue(Resource.loading(null)); // Đặt trạng thái là Đang tải
        authRepository.login(usernameOrEmail, password, rememberMe, result -> {
            if (attempt != loginAttempt) return;
            LoginResponse login = result.getData();
            if (result.getStatus() != Resource.Status.SUCCESS || login == null || login.getToken() == null
                    || isEmpty(login.getToken().getAccessToken())) {
                loginResult.setValue(result); // Lỗi, hoặc phản hồi thiếu token: UI tự hiển thị
                return;
            }
            SessionSnapshot session = sessionStore.login(login.getToken().getAccessToken(),
                    login.getToken().getRefreshToken(), login.isOnboardingCompleted(), rememberMe);
            loginSession = session;
            loginResult.setValue(result);
            // Gửi ngay trên luồng này nên request mang đúng phiên vừa tạo.
            loginProfile = userRepository.getProfile(profile -> onLoginProfile(attempt, session, login, profile));
        });
    }

    private void onLoginProfile(int attempt, SessionSnapshot session, LoginResponse login, Resource<UserProfileResponse> result) {
        if (attempt != loginAttempt) return;
        if (result.getStatus() == Resource.Status.SUCCESS && result.getData() != null) {
            loginSession = null;
            UserProfileResponse profile = result.getData();
            if (sessionStore.updateProfile(session, profile.getId(), profile.getEmail(), profile.getDisplayName()))
                loginNavigation.setValue(new Event<>(login.isOnboardingCompleted() ? LoginDestination.HOME : LoginDestination.SETUP));
        } else if (result.getStatus() == Resource.Status.ERROR) {
            discardLoginSession();
            loginResult.setValue(Resource.error(result.getError(), null));
        }
    }

    /** Xóa phiên dở dang (đã có token, chưa có hồ sơ) của lần đăng nhập đang chờ; không đụng tới phiên khác. */
    private void discardLoginSession() {
        loginProfile.cancel();
        if (loginSession != null) sessionStore.clear(loginSession);
        loginSession = null;
    }

    /**
     * Bỏ phiên đã lưu dở (đăng nhập nhưng chưa có userId) còn sót lại từ lần chạy trước.
     * Không làm gì khi chính ViewModel này đang chờ hồ sơ của lần đăng nhập hiện tại (Activity vừa được tạo lại).
     */
    public void discardIncompleteSession() {
        SessionSnapshot session = sessionStore.current();
        if (loginSession == null && session.loggedIn && isEmpty(session.userId)) sessionStore.clear(session);
    }

    @Override
    protected void onCleared() {
        // Màn hình đã đóng: phản hồi đến sau không được ghi phiên.
        loginAttempt++;
        discardLoginSession();
    }

    // android.text.TextUtils không dùng được trong JVM unit test.
    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** Thực hiện đăng ký tài khoản mới */
    public void register(String username, String email, String password) {
        this.registeredEmail = email; // Lưu lại email để dùng cho việc xác thực OTP sau đó
        registerResult.setValue(Resource.loading(null));
        authRepository.register(username, email, password, registerResult::setValue);
    }

    /** Xác thực email bằng mã OTP */
    public void verifyEmail(String email, String otp) {
        verifyEmailResult.setValue(Resource.loading(null));
        authRepository.verifyEmail(email, otp, verifyEmailResult::setValue);
    }

    /** Gửi lại email xác nhận tài khoản (/resend-verification) */
    public void resendVerification(String email) {
        resendVerificationResult.setValue(new Event<>(Resource.loading(null)));
        authRepository.resendVerification(email, result -> resendVerificationResult.setValue(new Event<>(result)));
    }

    /** Yêu cầu quên mật khẩu (gửi OTP về email) */
    public void forgotPassword(String email) {
        this.forgotPasswordEmail = email; // Lưu lại email dùng cho bước đặt lại mật khẩu
        forgotPasswordResult.setValue(new Event<>(Resource.loading(null)));
        authRepository.forgotPassword(email, result -> forgotPasswordResult.setValue(new Event<>(result)));
    }

    /** Đặt lại mật khẩu mới bằng email, mã OTP và mật khẩu mới */
    public void resetPassword(String email, String otp, String newPassword) {
        resetPasswordResult.setValue(Resource.loading(null));
        authRepository.resetPassword(email, otp, newPassword, resetPasswordResult::setValue);
    }

    /**
     * Đăng xuất khỏi thiết bị hiện tại. Phiên local của lúc bấm đăng xuất được xóa cả khi server báo lỗi;
     * một phiên mới hơn (nếu có) không bị ảnh hưởng.
     */
    public void logout() {
        SessionSnapshot origin = sessionStore.current();
        authRepository.logout(origin.refreshToken, result -> {
            if (!sessionStore.isCurrent(origin)) return;
            sessionStore.clear(origin);
            logoutResult.setValue(new Event<>(result));
        });
    }

    /**
     * Đổi mật khẩu tài khoản hiện tại.
     * Khi thành công, server trả về token pair mới và thu hồi các phiên khác.
     */
    public void changePassword(String currentPassword, String newPassword) {
        SessionSnapshot origin = sessionStore.current();
        changePasswordResult.setValue(new Event<>(Resource.loading(null)));
        authRepository.changePassword(currentPassword, newPassword, result -> {
            if (!sessionStore.isCurrent(origin)) return;
            if (result.getStatus() == Resource.Status.SUCCESS && result.getData() != null) {
                TokenResponse tokens = result.getData();
                sessionStore.updateTokens(origin, tokens.getAccessToken(), tokens.getRefreshToken());
            }
            changePasswordResult.setValue(new Event<>(result));
        });
    }
}
