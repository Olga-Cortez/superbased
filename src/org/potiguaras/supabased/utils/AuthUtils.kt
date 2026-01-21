package org.potiguaras.supabased.utils

import com.google.appinventor.components.runtime.util.JsonUtil
import com.google.appinventor.components.runtime.util.YailDictionary
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Bitbucket
import io.github.jan.supabase.auth.providers.Discord
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Gitlab
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Notion
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.Slack
import io.github.jan.supabase.auth.providers.Spotify
import io.github.jan.supabase.auth.providers.Twitch
import io.github.jan.supabase.auth.providers.Twitter
import io.github.jan.supabase.auth.providers.Zoom
import io.github.jan.supabase.auth.providers.builtin.Phone
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject
import javax.swing.UIManager.put
import kotlin.time.ExperimentalTime
import org.potiguaras.supabased.helpers.ThirdPartyProvider
import org.potiguaras.supabased.helpers.OTPType
import org.potiguaras.supabased.helpers.PhoneChannelOptions

class AuthUtils {
    fun getOAuthProvider(provider: ThirdPartyProvider): OAuthProvider {
        return when (provider) {
            ThirdPartyProvider.Google -> Google
            ThirdPartyProvider.GitHub -> Github
            ThirdPartyProvider.Facebook -> Facebook
            ThirdPartyProvider.Twitter -> Twitter
            ThirdPartyProvider.Apple -> Apple
            ThirdPartyProvider.Discord -> Discord
            ThirdPartyProvider.Twitch -> Twitch
            ThirdPartyProvider.Spotify -> Spotify
            ThirdPartyProvider.Slack -> Slack
            ThirdPartyProvider.Bitbucket -> Bitbucket
            ThirdPartyProvider.GitLab -> Gitlab
            ThirdPartyProvider.Azure -> Azure
            ThirdPartyProvider.Notion -> Notion
            ThirdPartyProvider.Zoom -> Zoom
        }
    }

    fun getEmailOtpType(type: OTPType): OtpType.Email {
        return when (type) {
            OTPType.INVITE -> OtpType.Email.INVITE
            OTPType.RECOVERY -> OtpType.Email.RECOVERY
            OTPType.EMAIL_CHANGE -> OtpType.Email.EMAIL_CHANGE
            OTPType.EMAIL-> OtpType.Email.EMAIL
            else -> throw IllegalArgumentException("Unknown OTP type: $type")
        }
    }

    fun getPhoneOtpType(type: OTPType): OtpType.Phone {
        return when (type) {
            OTPType.PHONE_CHANGE-> OtpType.Phone.PHONE_CHANGE
            OTPType.SMS-> OtpType.Phone.SMS
            else -> throw IllegalArgumentException("Unknown OTP type: $type")
        }
    }
    fun getPhoneChannelType(type: PhoneChannelOptions): Phone.Channel {
        return when (type) {
            PhoneChannelOptions.Sms -> Phone.Channel.SMS
            PhoneChannelOptions.Whatsapp -> Phone.Channel.WHATSAPP
        }
    }

    @OptIn(ExperimentalTime::class)
    fun userToYailDictionary(user: UserInfo?): YailDictionary {
        if (user == null) return YailDictionary()

        return try {
            val json = Json.encodeToString(UserInfo.serializer(), user)
            val jsonObject = JSONObject(json)
            convertJsonObjectToYailDictionary(jsonObject)
        } catch (e: Exception) {
            YailDictionary().apply {
                put("id", user.id)
                put("email", user.email ?: "")
                put("phone", user.phone ?: "")
                put("created_at", user.createdAt?.toString() ?: "")
                put("last_sign_in_at", user.lastSignInAt?.toString() ?: "")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun sessionToYailDictionary(session: UserSession?): YailDictionary {
        if (session == null) return YailDictionary()

        return try {
            val json = Json.encodeToString(UserSession.serializer(), session)
            val jsonObject = JSONObject(json)
            convertJsonObjectToYailDictionary(jsonObject)
        } catch (e: Exception) {
            YailDictionary().apply {
                put("access_token", session.accessToken)
                put("refresh_token", session.refreshToken)
                put("expires_at", session.expiresAt.toString())
                put("expires_in", session.expiresIn)
                put("token_type", session.tokenType)
            }
        }
    }

    fun convertToJsonObject(dict: YailDictionary): JsonObject {
        return try {
            val jsonString = JsonUtil.getJsonRepresentation(dict)
            Json.decodeFromString<JsonObject>(jsonString)
        } catch (e: Exception) {
            buildJsonObject {
                dict.keys.forEach { key ->
                    val keyStr = key.toString()
                    when (val value = dict[key]) {
                        is String -> put(keyStr, value)
                        is Number -> put(keyStr, value)
                        is Boolean -> put(keyStr, value)
                        is YailDictionary -> put(keyStr, convertToJsonObject(value))
                        is List<*> -> {
                            put(keyStr, buildJsonArray {
                                value.filterNotNull().forEach { item ->
                                    when (item) {
                                        is YailDictionary -> add(convertToJsonObject(item))
                                        else -> add(item.toString())
                                    }
                                }
                            })
                        }
                        null -> {}
                        else -> put(keyStr, value.toString())
                    }
                }
            }
        }
    }

    private fun convertJsonObjectToYailDictionary(jsonObject: JSONObject): YailDictionary {
        val dict = YailDictionary()
        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = jsonObject.get(key)) {
                is JSONObject -> {
                    dict[key] = convertJsonObjectToYailDictionary(value)
                }
                else -> {
                    dict[key] = value
                }
            }
        }

        return dict
    }
}