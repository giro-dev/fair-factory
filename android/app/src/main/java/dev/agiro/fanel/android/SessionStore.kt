package dev.agiro.fanel.android

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, null) ?: BuildConfig.API_BASE_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var householdId: String
        get() = prefs.getString(KEY_HOUSEHOLD_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOUSEHOLD_ID, value).apply()

    var householdName: String
        get() = prefs.getString(KEY_HOUSEHOLD_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOUSEHOLD_NAME, value).apply()

    var memberId: String
        get() = prefs.getString(KEY_MEMBER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MEMBER_ID, value).apply()

    var memberName: String
        get() = prefs.getString(KEY_MEMBER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MEMBER_NAME, value).apply()

    var assistantDevMode: Boolean
        get() = prefs.getBoolean(KEY_ASSISTANT_DEV, false)
        set(value) = prefs.edit().putBoolean(KEY_ASSISTANT_DEV, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val PREFS_NAME = "fanel_session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_HOUSEHOLD_ID = "household_id"
        const val KEY_HOUSEHOLD_NAME = "household_name"
        const val KEY_MEMBER_ID = "member_id"
        const val KEY_MEMBER_NAME = "member_name"
        const val KEY_ASSISTANT_DEV = "assistant_dev"
    }
}
