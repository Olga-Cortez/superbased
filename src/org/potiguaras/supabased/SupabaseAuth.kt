package org.potiguaras.supabased

import com.google.appinventor.components.annotations.DesignerComponent
import com.google.appinventor.components.annotations.SimpleEvent
import com.google.appinventor.components.annotations.SimpleFunction
import com.google.appinventor.components.annotations.SimpleProperty
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.EventDispatcher
import com.google.appinventor.components.runtime.OnDestroyListener
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailDictionary.*
import com.google.appinventor.components.runtime.util.JsonUtil.*
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.user.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Phone
import org.potiguaras.supabased.helpers.PhoneChannelOptions
import org.potiguaras.supabased.utils.AuthUtils
import org.potiguaras.supabased.helpers.ThirdPartyProvider
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.potiguaras.supabased.helpers.OTPType
import kotlin.String
import kotlin.text.ifEmpty

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Auth functions (implements auth-kt from supabase-kt kotlin library)",
    iconName = "icon.png"
)
@Suppress("FunctionName")
class SupabaseAuth(private val container: ComponentContainer) : AndroidNonvisibleComponent(container.`$form`()), OnDestroyListener {

    private val authUtils by lazy { AuthUtils() }

    private var _currentUser: YailDictionary = YailDictionary()
    private var _currentSession: YailDictionary = YailDictionary()

    private var _sessionManager: SessionManager? = null

    init {
        form.registerForOnDestroy(this)
        initializeSessionManager()
    }

    @SimpleProperty(description = "Check if Supabase client is initialized")
    fun IsClientInitialized(): Boolean = SupabaseCore.isInitialized()

    @SimpleProperty(description = "Get current user dictionary")
    fun CurrentUser(): YailDictionary = _currentUser

    @SimpleProperty(description = "Get current session dictionary")
    fun CurrentSession(): YailDictionary = _currentSession

    @SimpleProperty(description = "Check if user is authenticated")
    fun IsAuthenticated(): Boolean = _currentSession.isNotEmpty()

    // ========================== EVENTS =============================

    @SimpleEvent(description = "Triggered when user sign-up is successful")
    fun SignUpSuccess(
        userInfo: YailDictionary,
        email: String,
        userId: String,
        token: String
    ) {
        EventDispatcher.dispatchEvent(this, "SignUpSuccess",
            userInfo, email, userId, token)
    }

    @SimpleEvent(description = "Triggered when user sign-in is successful")
    fun SignInSuccess(
        email: String,
        userId: String,
        token: String,
        userInfo: YailDictionary
    ) {
        EventDispatcher.dispatchEvent(this, "SignInSuccess",
            email, userId, token, userInfo)
    }

    @SimpleEvent(description = "Triggered when user signs out")
    fun SignOutSuccess() {
        EventDispatcher.dispatchEvent(this, "SignOutSuccess")
    }

    @SimpleEvent(description = "Triggered when authentication error occurs")
    fun AuthError(error: String, errorCode: String = "") {
        EventDispatcher.dispatchEvent(this, "AuthError", error, errorCode)
    }

    @SimpleEvent(description = "Triggered when OTP is sent")
    fun OtpSent(channel: String, destination: String) {
        EventDispatcher.dispatchEvent(this, "OtpSent", channel, destination)
    }

    @SimpleEvent(description = "Triggered when password reset email is sent")
    fun ResetPasswordSent(email: String) {
        EventDispatcher.dispatchEvent(this, "ResetPasswordSent", email)
    }

    @SimpleEvent(description = "Triggered when user email is updated")
    fun EmailUpdated(oldEmail: String, newEmail: String) {
        EventDispatcher.dispatchEvent(this, "EmailUpdated", oldEmail, newEmail)
    }

    @SimpleEvent(description = "Triggered when user phone is updated")
    fun PhoneUpdated(oldPhone: String, newPhone: String) {
        EventDispatcher.dispatchEvent(this, "PhoneUpdated", oldPhone, newPhone)
    }

    @SimpleEvent(description = "Triggered when session is refreshed")
    fun SessionRefreshed(sessionInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "SessionRefreshed", sessionInfo)
    }

    @SimpleEvent(description = "Triggered when user data is updated")
    fun UserUpdated(userInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "UserUpdated", userInfo)
    }

    @SimpleEvent(description = "Triggered when MFA factor is enrolled")
    fun MfaTOTPFactorEnrolled(qrCode: String, qrCodeId: String, qrCodeType: String, factorId: String, factorType: String, friendlyName: String?, issuer: String?) {
        EventDispatcher.dispatchEvent(this, "MfaTOTPFactorEnrolled", qrCode, qrCodeId, qrCodeType, factorId, factorType, friendlyName, issuer)
    }

    @SimpleEvent(description = "Triggered when MFA factor is enrolled")
    fun MfaPhoneFactorEnrolled(phone: String, factorId: String, factorType: String, friendlyName: String?) {
        EventDispatcher.dispatchEvent(this, "MfaPhoneFactorEnrolled", phone, factorId, factorType, friendlyName)
    }

    @SimpleEvent(description = "Triggered when MFA factor is verified")
    fun MfaFactorVerified() {
        EventDispatcher.dispatchEvent(this, "MfaFactorVerified")
    }

    @SimpleEvent(description = "Triggered when MFA factor is verified")
    fun MfaChallengeCreated(challengeId: String) {
        EventDispatcher.dispatchEvent(this, "MfaChallengeCreated", challengeId)
    }

    // ==================== SIGN UP FUNCTIONS ====================

    @SimpleFunction(description = "Sign up new user with email and password")
    fun SignUpWithEmail(
        email: String,
        password: String,
        userMetadata: YailDictionary = YailDictionary(),
        redirectTo: String = "",
        captchaToken: String = ""
    ) {
        executeAuthOperation { client ->
            client.auth.signUpWith(provider=Email, redirectUrl=redirectTo.ifEmpty { null }) {
                this.email = email
                this.password = password
                if (userMetadata.isNotEmpty()) {
                    this.data = authUtils.convertToJsonObject(userMetadata)
                }
                if (captchaToken.isNotEmpty()) {
                    this.captchaToken = captchaToken
                }
            }
        }
    }

    @SimpleFunction(description = "Sign up new user with phone and password")
    fun SignUpWithPhone(
        phone: String,
        password: String,
        channel: PhoneChannelOptions = PhoneChannelOptions.Sms,
        userMetadata: YailDictionary = YailDictionary(),
        redirectTo: String = "",
        captchaToken: String = ""
    ) {
        executeAuthOperation { client ->
            client.auth.signUpWith(provider=Phone, redirectUrl=redirectTo.ifEmpty { null }) {
                this.phone = phone
                this.password = password

                this.channel = when (channel) {
                    PhoneChannelOptions.Whatsapp -> Phone.Channel.WHATSAPP
                    else -> Phone.Channel.SMS
                }
                if (userMetadata.isNotEmpty()) {
                    data = authUtils.convertToJsonObject(userMetadata)
                }
                if (captchaToken.isNotEmpty()) {
                    this.captchaToken = captchaToken
                }
            }
        }
    }

    // ==================== SIGN IN FUNCTIONS ====================

    @SimpleFunction(description = "Sign in with email and password")
    fun SignInWithEmail(email: String, password: String, redirectTo: String = "") {
        executeAuthOperation { client ->
            client.auth.signInWith(provider=Email, redirectUrl=redirectTo.ifEmpty { null }) {
                this.email = email
                this.password = password
            }
            updateUserAndSession(client)
        }
    }

    @SimpleFunction(description = "Sign in with phone and password")
    fun SignInWithPhone(phone: String, password: String, redirectTo: String = "") {
        executeAuthOperation { client ->
            client.auth.signInWith(provider = Phone, redirectUrl=redirectTo.ifEmpty { null }) {
                this.phone = phone
                this.password = password
            }
            updateUserAndSession(client)
        }
    }

    @SimpleFunction(description = "Sign in with OTP via email")
    fun SignInWithEmailOtp(email: String, redirectTo: String = "") {
        executeAuthOperation { client ->
            client.auth.signInWith(provider=OTP, redirectUrl=redirectTo.ifEmpty { null }) {
                this.email = email
            }
            OtpSent("email", email)
        }
    }

    @SimpleFunction(description = "Sign in with SMS OTP")
    fun SignInWithPhoneOtp(
        phone: String,
        redirectTo: String = "",
        shouldCreateUser: Boolean = true
    ) {
        executeAuthOperation { client ->
            client.auth.signInWith(provider=OTP, redirectUrl=redirectTo.ifEmpty { null }) {
                this.phone = phone
                createUser = shouldCreateUser
            }
            OtpSent("phone", phone)
        }
    }

    @SimpleFunction(description = "Sign in with OAuth provider")
    fun SignInWithOAuth(
        provider: ThirdPartyProvider,
        redirectTo: String = "",
        scopesList: YailList? = null,
    ) {
        executeAuthOperation { client ->
            val oauthProvider = authUtils.getOAuthProvider(provider)
            client.auth.signInWith(provider = oauthProvider, redirectUrl = redirectTo.ifEmpty { null }) {
                if (!scopesList.isNullOrEmpty()) {
                    val scopesArray = scopesList.toStringArray()
                    scopesArray.forEach { scope ->
                        scopes.add(scope)
                    }
                }
            }
        }
    }

    @SimpleFunction(description = "Verify OTP code")
    fun VerifyOtp(
        email: String? = null,
        phone: String? = null,
        token: String,
        type: OTPType
    ) {
        executeAuthOperation { client ->
            if (email != null) {
                client.auth.verifyEmailOtp(
                    type = authUtils.getEmailOtpType(type),
                    email = email,
                    token = token
                )
            } else if (phone != null) {
                client.auth.verifyPhoneOtp(
                    type = authUtils.getPhoneOtpType(type),
                    phone = phone,
                    token = token
                )
            }

            updateUserAndSession(client)
        }
    }

    // ==================== PASSWORD MANAGEMENT ====================

    @SimpleFunction(description = "Reset password for email")
    fun ResetPasswordForEmail(
        email: String,
        redirectTo: String = "",
        captchaToken: String = ""
    ) {
        executeAuthOperation { client ->
            client.auth.resetPasswordForEmail(
                email = email,
                redirectUrl = redirectTo.ifEmpty { null },
                captchaToken = captchaToken.ifEmpty { null }
            )
            ResetPasswordSent(email)
        }
    }

    @SimpleFunction(description = "Update user password")
    fun UpdatePassword(newPassword: String) {
        executeAuthOperation { client ->
            client.auth.updateUser {
                password = newPassword
            }
            updateUserAndSession(client)
        }
    }

    // ==================== USER MANAGEMENT ====================

    @SimpleFunction(description = "Get current user info")
    fun GetUser(): YailDictionary {
        return try {
            val client = getClient() ?: return YailDictionary()
            client.auth.currentUserOrNull()?.let { user ->
                authUtils.userToYailDictionary(user)
            } ?: YailDictionary()
        } catch (e: Exception) {
            YailDictionary()
        }
    }

    @SimpleFunction(description = "Update user information")
    fun UpdateUser(
        email: String? = null,
        phone: String? = null,
        password: String? = null,
        userMetadata: YailDictionary = YailDictionary()
    ) {
        executeAuthOperation { client ->
            val oldEmail = client.auth.currentUserOrNull()?.email
            val oldPhone = client.auth.currentUserOrNull()?.phone

            client.auth.updateUser {
                email?.let { this.email = it }
                phone?.let { this.phone = it }
                password?.let { this.password = it }

                if (userMetadata.isNotEmpty()) {
                    data = authUtils.convertToJsonObject(userMetadata)
                }
            }

            updateUserAndSession(client)

            email?.let { newEmail ->
                oldEmail?.let { old ->
                    if (old != newEmail) {
                        mainHandler.launch {
                            EmailUpdated(old, newEmail)
                        }
                    }
                }
            }

            phone?.let { newPhone ->
                oldPhone?.let { old ->
                    if (old != newPhone) {
                        mainHandler.launch {
                            PhoneUpdated(old, newPhone)
                        }
                    }
                }
            }
        }
    }

    @SimpleFunction(description = "Soft delete current user")
    fun SoftDeleteCurrentUser() {
        executeAuthOperation { client ->
            val currentUser = client.auth.currentUserOrNull()
            if (currentUser != null) {
                client.auth.admin.updateUserById(uid = currentUser.id) {
                    userMetadata = buildJsonObject {
                        put("deleted", true)
                        put("deleted_at", System.currentTimeMillis().toString())
                    }
                }
            } else {
                throw Exception("No user logged in")
            }
            clearUserSession()
            mainHandler.launch {
                SignOutSuccess()
            }
        }
    }

    @SimpleFunction(description = "Soft delete user by ID (admin only)")
    fun SoftDeleteUser(userId: String) {
        executeAuthOperation { client ->
            client.auth.admin.updateUserById(uid = userId) {
                userMetadata = buildJsonObject {
                    put("deleted", true)
                    put("deleted_at", System.currentTimeMillis().toString())
                }
            }

            val currentUserId = client.auth.currentUserOrNull()?.id
            if (currentUserId == userId) {
                clearUserSession()
                mainHandler.launch {
                    SignOutSuccess()
                }
            }
        }
    }

    // ==================== SESSION MANAGEMENT ====================

    @SimpleFunction(description = "Sign out current user")
    fun SignOut() {
        executeAuthOperation { client ->
            client.auth.signOut()
            clearUserSession()
            mainHandler.launch {
                SignOutSuccess()
            }
        }
    }

    @SimpleFunction(description = "Refresh current session")
    fun RefreshSession() {
        executeAuthOperation { client ->
            client.auth.refreshCurrentSession()
            _currentSession = authUtils.sessionToYailDictionary(client.auth.currentSessionOrNull())
            mainHandler.launch {
                SessionRefreshed(_currentSession)
            }
        }
    }

    // ==================== MFA FUNCTIONS ====================

    @SimpleFunction(description = "Enroll MFA factor")
    fun MfaEnrollTOTPFactor(
        friendlyName: String = "",
        issuerUrl: String = "",
    ) {
        executeAuthOperation { client ->
           val issuerURL=issuerUrl.ifEmpty { null }
           val frndName = friendlyName.ifEmpty { null }
           val factor = client.auth.mfa.enroll(
               factorType = FactorType.TOTP,
               friendlyName = frndName
           ) {
               issuer=issuerURL
           }
                val (id, type, uri) = factor.data
                val (factorId, factorType, _) = factor
            mainHandler.launch {
                MfaTOTPFactorEnrolled(qrCode=uri, qrCodeId =id, qrCodeType = type, factorId = factorId, factorType = factorType, friendlyName=frndName, issuer=issuerURL)
            }
        }
    }

    @SimpleFunction(description = "Enroll MFA factor")
    fun MfaEnrollPhoneFactor(
        phone: String = "",
        friendlyName: String = ""
    ) {
        executeAuthOperation { client ->
            val factor = client.auth.mfa.enroll(
                factorType = FactorType.Phone,
                friendlyName = friendlyName.ifEmpty { null }
            ) {
                this.phone = phone
            }

            val (phone) = factor.data
            val (factorId, factorType, _) = factor

            mainHandler.launch {
                MfaPhoneFactorEnrolled(phone, factorId, factorType, friendlyName)
            }
        }
    }

    @SimpleFunction(description = "Creates a challenge for a factor.")
    fun MfaCreateChallenge(
        factorId: String,
        channel: PhoneChannelOptions
    ) {
        executeAuthOperation { client ->
            val challenge = client.auth.mfa.createChallenge(factorId = factorId, channel=authUtils.getPhoneChannelType(channel))

            val challengeId = challenge.id
            mainHandler.launch {
                MfaChallengeCreated(challengeId = challengeId)
            }
        }
    }

    @SimpleFunction(description = "Verifies a challenge for a factor. To verify a challenge, please create a challenge first.")
    fun VerifyMFAFactorChallenge(
        factorId: String,
        code: String,
        challengeId: String,
        shouldSaveSession: Boolean = true
    ) {
        executeAuthOperation { client ->
            val verifyResponse = client.auth.mfa.verifyChallenge(
                factorId = factorId,
                code = code,
                challengeId = challengeId,
                saveSession = shouldSaveSession
            )

            mainHandler.launch {
                MfaFactorVerified()
            }
        }
    }

    @SimpleFunction(description = "Get available MFA factors")
    fun GetMfaFactors(): List<YailDictionary> {
        return try {
            val client = getClient() ?: throw Exception("Supabase client not initialized")
            val user = client.auth.currentUserOrNull() ?: return emptyList()

            user.factors.map { factor ->
                YailDictionary().apply {
                    put("id", factor.id)
                    put("type", factor.factorType)
                    put("friendly_name", factor.friendlyName)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val scope = SupabaseCore.scope
    private val mainHandler = SupabaseCore.mainHandler

    private fun getClient(): SupabaseClient? {
        return SupabaseCore.getClient()
    }

    private fun initializeSessionManager() {
        _sessionManager = SessionManager().apply {
            onSessionUpdate = { session ->
                _currentSession = authUtils.sessionToYailDictionary(session)
            }
            onUserUpdate = { user ->
                _currentUser = authUtils.userToYailDictionary(user)
            }
        }
    }

    private fun executeAuthOperation(operation: suspend (SupabaseClient) -> Unit) {
        scope.launch {
            try {
                val client = getClient() ?: throw Exception("Supabase client not initialized")
                operation(client)
            } catch (e: Exception) {
                mainHandler.launch {
                    AuthError(e.message ?: "Unknown error", e.javaClass.simpleName)
                }
            }
        }
    }

    private fun updateUserAndSession(client: SupabaseClient) {
        val user = client.auth.currentUserOrNull()
        val session = client.auth.currentSessionOrNull()

        user?.let {
            _currentUser = authUtils.userToYailDictionary(it)
        }

        session?.let {
            _currentSession = authUtils.sessionToYailDictionary(it)

            mainHandler.launch {
                SignInSuccess(
                    user?.email ?: "",
                    user?.id ?: "",
                    it.accessToken,
                    _currentUser
                )
            }
        }
    }

    private fun clearUserSession() {
        _currentUser = YailDictionary()
        _currentSession = YailDictionary()
        _sessionManager?.clearSession()
    }

    override fun onDestroy() {
        _sessionManager?.clearSession()
        _sessionManager = null
    }

    // ==================== INNER CLASSES ====================

    private class SessionManager {
        var onSessionUpdate: ((UserSession) -> Unit)? = null
        var onUserUpdate: ((UserInfo) -> Unit)? = null

        fun clearSession() {
            onSessionUpdate = null
            onUserUpdate = null
        }
    }
}