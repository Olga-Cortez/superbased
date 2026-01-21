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
import com.google.appinventor.components.runtime.util.YailList
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.TokenExpiredException
import io.github.jan.supabase.auth.exception.SessionRequiredException
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.user.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.builtin.Phone
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.potiguaras.supabased.helpers.PhoneChannelOptions
import org.potiguaras.supabased.utils.AuthUtils
import org.potiguaras.supabased.helpers.ThirdPartyProvider
import org.potiguaras.supabased.helpers.OTPType

@DesignerComponent(
    version = 59,
    versionName = "1.0",
    description = "Extension block for using Auth functions (implements auth-kt from supabase-kt kotlin library)",
    iconName = "icon.png",
    nonVisible = true,
    category = com.google.appinventor.components.common.ComponentCategory.EXTENSION
)
@Suppress("FunctionName", "unused")  // Added "unused" here
class SupabaseAuth(
    private val container: ComponentContainer
) : AndroidNonvisibleComponent(container.`$form`()),
    OnDestroyListener {

    private val authUtils by lazy { AuthUtils() }
    private val componentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableListOf<Job>()

    @Volatile
    private var _currentUser: YailDictionary = YailDictionary()

    @Volatile
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

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user sign-up is successful")
    fun SignUpWithEmailSuccess(
        userInfo: YailDictionary,
        email: String,
        userId: String,
        token: String
    ) {
        EventDispatcher.dispatchEvent(this, "SignUpWithEmailSuccess",
            userInfo, email, userId, token)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user sign-up with phone is successful")
    fun SignUpWithPhoneSuccess(
        userInfo: YailDictionary,
        phone: String,
        userId: String,
        token: String
    ) {
        EventDispatcher.dispatchEvent(this, "SignUpWithPhoneSuccess",
            userInfo, phone, userId, token)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user sign-in is successful")
    fun SignInWithEmailSuccess(
        email: String,
        userId: String,
        token: String,
        userInfo: YailDictionary
    ) {
        EventDispatcher.dispatchEvent(this, "SignInWithEmailSuccess",
            email, userId, token, userInfo)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user sign-in is successful")
    fun SignInWithPhoneSuccess(
        phone: String,
        userId: String,
        token: String,
        userInfo: YailDictionary
    ) {
        EventDispatcher.dispatchEvent(this, "SignInWithPhoneSuccess",
            phone, userId, token, userInfo)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user signs out")
    fun SignOutSuccess() {
        EventDispatcher.dispatchEvent(this, "SignOutSuccess")
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when any authentication error occurs")
    fun AuthError(operation: String, errorMessage: String, errorCode: String = "", httpStatus: Int = -1) {
        EventDispatcher.dispatchEvent(this, "AuthError", operation, errorMessage, errorCode, httpStatus)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when OTP is sent")
    fun OtpSent(channel: String, destination: String) {
        EventDispatcher.dispatchEvent(this, "OtpSent", channel, destination)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when password reset email is sent")
    fun ResetPasswordSent(email: String) {
        EventDispatcher.dispatchEvent(this, "ResetPasswordSent", email)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user email is updated")
    fun EmailUpdated(oldEmail: String, newEmail: String) {
        EventDispatcher.dispatchEvent(this, "EmailUpdated", oldEmail, newEmail)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user phone is updated")
    fun PhoneUpdated(oldPhone: String, newPhone: String) {
        EventDispatcher.dispatchEvent(this, "PhoneUpdated", oldPhone, newPhone)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when session is refreshed")
    fun SessionRefreshed(sessionInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "SessionRefreshed", sessionInfo)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when user data is updated")
    fun UserUpdated(userInfo: YailDictionary) {
        EventDispatcher.dispatchEvent(this, "UserUpdated", userInfo)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when MFA factor is enrolled")
    fun MfaTOTPFactorEnrolled(qrCode: String, qrCodeId: String, qrCodeType: String, factorId: String, factorType: String, friendlyName: String?, issuer: String?) {
        EventDispatcher.dispatchEvent(this, "MfaTOTPFactorEnrolled", qrCode, qrCodeId, qrCodeType, factorId, factorType, friendlyName, issuer)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when MFA factor is enrolled")
    fun MfaPhoneFactorEnrolled(phone: String, factorId: String, factorType: String, friendlyName: String?) {
        EventDispatcher.dispatchEvent(this, "MfaPhoneFactorEnrolled", phone, factorId, factorType, friendlyName)
    }

    @Suppress("unused")  // Added this annotation
    @SimpleEvent(description = "Triggered when MFA factor is verified")
    fun MfaFactorVerified(factorId: String, challengeId: String, sessionUpdated: Boolean) {
        EventDispatcher.dispatchEvent(this, "MfaFactorVerified", factorId, challengeId, sessionUpdated)
    }

    @Suppress("unused")  // Added this annotation
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
        executeAuthOperation("SignUpWithEmail") { client, onSuccess, onError ->
            try {
                val result = client.auth.signUpWith(
                    provider = Email,
                    redirectUrl = redirectTo.ifEmpty { null }
                ) {
                    this.email = email
                    this.password = password
                    if (userMetadata.isNotEmpty()) {
                        this.data = authUtils.convertToJsonObject(userMetadata)
                    }
                    if (captchaToken.isNotEmpty()) {
                        this.captchaToken = captchaToken
                    }
                }

                val session = client.auth.currentSessionOrNull()
                updateUserAndSession(client)

                onSuccess {
                    SignUpWithEmailSuccess(_currentUser, email, result?.id ?: "", session?.accessToken ?: "")
                }
            } catch (e: Exception) {
                onError(e)
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
        executeAuthOperation("SignUpWithPhone") { client, onSuccess, onError ->
            try {
                val result = client.auth.signUpWith(provider = Phone, redirectUrl = redirectTo.ifEmpty { null }) {
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

                val session = client.auth.currentSessionOrNull()
                updateUserAndSession(client)

                onSuccess {
                    SignUpWithPhoneSuccess(_currentUser, result?.phone ?: "", result?.id ?: "", session?.accessToken ?: "")
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // ==================== SIGN IN FUNCTIONS ====================

    @SimpleFunction(description = "Sign in with email and password")
    fun SignInWithEmail(email: String, password: String, redirectTo: String = "") {
        executeAuthOperation("SignInWithEmail") { client, onSuccess, onError ->
            try {
                client.auth.signInWith(
                    provider = Email,
                    redirectUrl = redirectTo.ifEmpty { null }
                ) {
                    this.email = email
                    this.password = password
                }

                updateUserAndSession(client)

                onSuccess {
                    SignInWithEmailSuccess(email, _currentUser["id"] as String, _currentSession["token"] as String, _currentUser)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Sign in with phone and password")
    fun SignInWithPhone(phone: String, password: String, redirectTo: String = "") {
        executeAuthOperation("SignInWithPhone") { client, onSuccess, onError ->
            try {
                client.auth.signInWith(
                    provider = Phone,
                    redirectUrl = redirectTo.ifEmpty { null }
                ) {
                    this.phone = phone
                    this.password = password
                }

                updateUserAndSession(client)

                onSuccess {
                    SignInWithPhoneSuccess(
                        _currentUser["phone"] as String,
                        _currentUser["id"] as String,
                        _currentSession["token"] as String,
                        _currentUser
                    )
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Sign in with OTP via email")
    fun SignInWithEmailOtp(email: String, redirectTo: String = "") {
        executeAuthOperation("SignInWithEmailOtp") { client, onSuccess, onError ->
            try {
                client.auth.signInWith(provider = OTP, redirectUrl = redirectTo.ifEmpty { null }) {
                    this.email = email
                }

                onSuccess {
                    OtpSent("email", email)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Sign in with SMS OTP")
    fun SignInWithPhoneOtp(
        phone: String,
        redirectTo: String = "",
        shouldCreateUser: Boolean = true
    ) {
        executeAuthOperation("SignInWithPhoneOtp") { client, onSuccess, onError ->
            try {
                client.auth.signInWith(provider = OTP, redirectUrl = redirectTo.ifEmpty { null }) {
                    this.phone = phone
                    createUser = shouldCreateUser
                }

                onSuccess {
                    OtpSent("phone", phone)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Sign in with OAuth provider")
    fun SignInWithOAuth(
        provider: ThirdPartyProvider,
        redirectTo: String = "",
        scopesList: YailList = YailList.makeEmptyList(),
    ) {
        executeAuthOperation("SignInWithOAuth") { client, onSuccess, onError ->
            try {
                val oauthProvider = authUtils.getOAuthProvider(provider)
                client.auth.signInWith(provider = oauthProvider, redirectUrl = redirectTo.ifEmpty { null }) {
                    if (scopesList.isNotEmpty()) {
                        val scopesArray = scopesList.toStringArray()
                        scopesArray.forEach { scope ->
                            scopes.add(scope)
                        }
                    }
                }

                onSuccess {
                    // OAuth flow will redirect, no immediate success event
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Verify OTP code sent to email")
    fun VerifyEmailOtp(
        email: String,
        token: String,
        type: OTPType
    ) {
        executeAuthOperation("VerifyEmailOtp") { client, onSuccess, onError ->
            try {
                client.auth.verifyEmailOtp(
                    type = authUtils.getEmailOtpType(type),
                    email = email,
                    token = token
                )

                updateUserAndSession(client)

                onSuccess {
                    // Event will be triggered by updateUserAndSession
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Verify OTP code sent to phone")
    fun VerifyPhoneOtp(
        phone: String,
        token: String,
        type: OTPType
    ) {
        executeAuthOperation("VerifyPhoneOtp") { client, onSuccess, onError ->
            try {
                client.auth.verifyPhoneOtp(
                    type = authUtils.getPhoneOtpType(type),
                    phone = phone,
                    token = token
                )

                updateUserAndSession(client)

                onSuccess {
                    // Event will be triggered by updateUserAndSession
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // ==================== PASSWORD MANAGEMENT ====================

    @SimpleFunction(description = "Reset password for email")
    fun ResetPasswordForEmail(
        email: String,
        redirectTo: String = "",
        captchaToken: String = ""
    ) {
        executeAuthOperation("ResetPasswordForEmail") { client, onSuccess, onError ->
            try {
                client.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = redirectTo.ifEmpty { null },
                    captchaToken = captchaToken.ifEmpty { null }
                )

                onSuccess {
                    ResetPasswordSent(email)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Update user password")
    fun UpdatePassword(newPassword: String) {
        executeAuthOperation("UpdatePassword") { client, onSuccess, onError ->
            try {
                client.auth.updateUser {
                    password = newPassword
                }

                updateUserAndSession(client)

                onSuccess {
                    UserUpdated(_currentUser)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // ==================== USER MANAGEMENT ====================

    @SimpleFunction(description = "Get current user info")
    fun GetUser(): YailDictionary {
        return try {
            val client = SupabaseCore.getClient() ?: return YailDictionary()
            client.auth.currentUserOrNull()?.let { user ->
                authUtils.userToYailDictionary(user)
            } ?: YailDictionary()
        } catch (e: Exception) {
            YailDictionary()
        }
    }

    @SimpleFunction(description = "Modifies the logged user's information")
    fun UpdateUser(
        email: String = "",
        phone: String = "",
        password: String = "",
        userMetadata: YailDictionary = YailDictionary()
    ) {
        executeAuthOperation("UpdateUser") { client, onSuccess, onError ->
            try {
                if (email.isEmpty() && phone.isEmpty() && password.isEmpty() && userMetadata.isEmpty) {
                    throw IllegalArgumentException("At least one field must be provided for update")
                }

                val currentUser = client.auth.currentUserOrNull()
                    ?: throw SessionRequiredException("No user is currently logged in")

                val oldEmail = currentUser.email
                val oldPhone = currentUser.phone

                client.auth.updateUser {
                    if (email.isNotEmpty()) {
                        this.email = email
                    }
                    if (phone.isNotEmpty()) {
                        this.phone = phone
                    }
                    if (password.isNotEmpty()) {
                        this.password = password
                    }
                    if (userMetadata.isNotEmpty()) {
                        data = authUtils.convertToJsonObject(userMetadata)
                    }
                }

                updateUserAndSession(client)

                onSuccess {
                    UserUpdated(_currentUser)

                    if (email.isNotEmpty() && oldEmail != email) {
                        EmailUpdated(oldEmail ?: "", email)
                    }

                    if (phone.isNotEmpty() && oldPhone != phone) {
                        PhoneUpdated(oldPhone ?: "", phone)
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Soft delete current user")
    fun SoftDeleteCurrentUser() {
        executeAuthOperation("SoftDeleteCurrentUser") { client, onSuccess, onError ->
            try {
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

                onSuccess {
                    SignOutSuccess()
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Soft delete user by ID (admin only)")
    fun SoftDeleteUser(userId: String) {
        executeAuthOperation("SoftDeleteUser") { client, onSuccess, onError ->
            try {
                client.auth.admin.updateUserById(uid = userId) {
                    userMetadata = buildJsonObject {
                        put("deleted", true)
                        put("deleted_at", System.currentTimeMillis().toString())
                    }
                }

                val currentUserId = client.auth.currentUserOrNull()?.id
                if (currentUserId == userId) {
                    clearUserSession()

                    onSuccess {
                        SignOutSuccess()
                    }
                } else {
                    onSuccess {
                        // User deleted but not the current user
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // ==================== SESSION MANAGEMENT ====================

    @SimpleFunction(description = "Sign out current user")
    fun SignOut() {
        executeAuthOperation("SignOut") { client, onSuccess, onError ->
            try {
                client.auth.signOut()
                clearUserSession()

                onSuccess {
                    SignOutSuccess()
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Refresh current session")
    fun RefreshSession() {
        executeAuthOperation("RefreshSession") { client, onSuccess, onError ->
            try {
                client.auth.refreshCurrentSession()
                _currentSession = authUtils.sessionToYailDictionary(client.auth.currentSessionOrNull())

                onSuccess {
                    SessionRefreshed(_currentSession)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // ==================== MFA FUNCTIONS ====================

    @SimpleFunction(description = "Enroll MFA factor")
    fun MfaEnrollTOTPFactor(
        friendlyName: String = "",
        issuerUrl: String = "",
    ) {
        executeAuthOperation("MfaEnrollTOTPFactor") { client, onSuccess, onError ->
            try {
                val issuerURL = issuerUrl.ifEmpty { null }
                val frndName = friendlyName.ifEmpty { null }
                val factor = client.auth.mfa.enroll(
                    factorType = FactorType.TOTP,
                    friendlyName = frndName
                ) {
                    issuer = issuerURL
                }

                val (id, type, uri) = factor.data
                val (factorId, factorType, _) = factor

                onSuccess {
                    MfaTOTPFactorEnrolled(
                        qrCode = uri,
                        qrCodeId = id,
                        qrCodeType = type,
                        factorId = factorId,
                        factorType = factorType,
                        friendlyName = frndName,
                        issuer = issuerURL
                    )
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Enroll MFA factor")
    fun MfaEnrollPhoneFactor(
        phone: String = "",
        friendlyName: String = ""
    ) {
        executeAuthOperation("MfaEnrollPhoneFactor") { client, onSuccess, onError ->
            try {
                val factor = client.auth.mfa.enroll(
                    factorType = FactorType.Phone,
                    friendlyName = friendlyName.ifEmpty { null }
                ) {
                    this.phone = phone
                }

                val (phone) = factor.data
                val (factorId, factorType, _) = factor

                onSuccess {
                    MfaPhoneFactorEnrolled(phone, factorId, factorType, friendlyName)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Creates a challenge for a factor.")
    fun MfaCreateChallenge(
        factorId: String,
        channel: PhoneChannelOptions
    ) {
        executeAuthOperation("MfaCreateChallenge") { client, onSuccess, onError ->
            try {
                val challenge = client.auth.mfa.createChallenge(
                    factorId = factorId,
                    channel = authUtils.getPhoneChannelType(channel)
                )

                val challengeId = challenge.id

                onSuccess {
                    MfaChallengeCreated(challengeId = challengeId)
                }
            } catch (e: Exception) {
                onError(e)
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
        executeAuthOperation("VerifyMFAFactorChallenge") { client, onSuccess, onError ->
            try {
                val verifyResponse = client.auth.mfa.verifyChallenge(
                    factorId = factorId,
                    code = code,
                    challengeId = challengeId,
                    saveSession = shouldSaveSession
                )

                // If saveSession is true, update the session
                if (shouldSaveSession) {
                    _currentSession = authUtils.sessionToYailDictionary(verifyResponse)
                    verifyResponse.user?.let { user ->
                        _currentUser = authUtils.userToYailDictionary(user)
                    }
                }

                onSuccess {
                    // Pass more information to the event
                    MfaFactorVerified(factorId, challengeId, shouldSaveSession)

                    // If session was saved, also trigger session refresh event
                    if (shouldSaveSession) {
                        SessionRefreshed(_currentSession)
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    @SimpleFunction(description = "Get available MFA factors")
    fun GetMfaFactors(): List<YailDictionary> {
        return try {
            val client = SupabaseCore.getClient() ?: return mutableListOf()
            val currentUser = client.auth.currentUserOrNull() ?: return mutableListOf()

            val factorsList = mutableListOf<YailDictionary>()
            currentUser.factors.forEach { factor ->
                YailDictionary().apply {
                    put("id", factor.id)
                    put("type", factor.factorType)
                    put("friendly_name", factor.friendlyName)
                }.also { dict ->
                    factorsList.add(dict)
                }
            }
            factorsList
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    // ==================== PRIVATE METHODS ====================

    private fun executeAuthOperation(
        operationName: String,
        operation: suspend (
            SupabaseClient,
            onSuccess: (() -> Unit) -> Unit,
            onError: (Exception) -> Unit
        ) -> Unit
    ) {
        if (!SupabaseCore.isInitialized()) {
            form.runOnUiThread {
                AuthError(operationName, "Supabase client not initialized", "NOT_INITIALIZED", -1)
            }
            return
        }

        val job = componentScope.launch {
            try {
                val client = SupabaseCore.getClient() ?: throw IllegalStateException("Client not initialized")

                operation(client,
                    { successCallback ->
                        form.runOnUiThread {
                            successCallback()
                        }
                    },
                    { error ->
                        handleError(operationName, error)
                    }
                )
            } catch (e: CancellationException) {
                // Operation was cancelled, do nothing
            } catch (e: Exception) {
                handleError(operationName, e)
            }
        }

        trackJob(job)
    }

    private fun handleError(operationName: String, error: Exception) {
        form.runOnUiThread {
            when (error) {
                is AuthRestException -> {
                    val code = error.errorCode?.value ?: error.error
                    AuthError(operationName, error.errorDescription, code, error.statusCode)
                }
                is RestException -> {
                    AuthError(operationName, error.description ?: error.error, error.error, error.statusCode)
                }
                is TokenExpiredException -> {
                    AuthError(operationName, error.message ?: "Token expired", "token_expired", 401)
                }
                is SessionRequiredException -> {
                    AuthError(operationName, error.message ?: "Session required", "session_required", 401)
                }
                else -> {
                    AuthError(operationName, error.message ?: "Unknown error", error.javaClass.simpleName, -1)
                }
            }
        }
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

    private class SessionManager {
        var onSessionUpdate: ((UserSession) -> Unit)? = null
        var onUserUpdate: ((UserInfo) -> Unit)? = null

        fun clearSession() {
            onSessionUpdate = null
            onUserUpdate = null
        }
    }

    private fun updateUserAndSession(client: SupabaseClient) {
        val user = client.auth.currentUserOrNull()
        val session = client.auth.currentSessionOrNull()

        user?.let {
            _currentUser = authUtils.userToYailDictionary(it)
            form.runOnUiThread {
                UserUpdated(_currentUser)
            }
        } ?: run {
            _currentUser = YailDictionary()
        }

        session?.let {
            _currentSession = authUtils.sessionToYailDictionary(it)
            form.runOnUiThread {
                SessionRefreshed(_currentSession)
            }
        } ?: run {
            _currentSession = YailDictionary()
        }
    }

    private fun clearUserSession() {
        _currentUser = YailDictionary()
        _currentSession = YailDictionary()
        _sessionManager?.clearSession()
    }

    private fun trackJob(job: Job) {
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    private fun cancelAllJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    override fun onDestroy() {
        cancelAllJobs()
        componentScope.cancel()
        _sessionManager?.clearSession()
        _sessionManager = null
    }
}