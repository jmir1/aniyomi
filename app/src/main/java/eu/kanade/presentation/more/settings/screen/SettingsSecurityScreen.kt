package eu.kanade.presentation.more.settings.screen

import dev.icerock.moko.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import tachiyomi.presentation.core.i18n.localizePlural
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val authSupported = remember { context.isAuthenticationSupported() }

        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = useAuthPref,
                title = localize(MR.strings.lock_with_biometrics),
                enabled = authSupported,
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.localize(MR.strings.lock_with_biometrics),
                    )
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.lockAppAfter(),
                title = localize(MR.strings.lock_when_idle),
                enabled = authSupported && useAuth,
                entries = LockAfterValues
                    .associateWith {
                        when (it) {
                            -1 -> localize(MR.strings.lock_never)
                            0 -> localize(MR.strings.lock_always)
                            else -> localizePlural(
                                MR.plurals.lock_after_mins,
                                count = it,
                                it,
                            )
                        }
                    },
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.localize(MR.strings.lock_when_idle),
                    )
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = securityPreferences.hideNotificationContent(),
                title = localize(MR.strings.hide_notification_content),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.secureScreen(),
                title = localize(MR.strings.secure_screen),
                entries = SecurityPreferences.SecureScreenMode.entries
                    .associateWith { localize(it.titleRes) },
            ),
            Preference.PreferenceItem.InfoPreference(localize(MR.strings.secure_screen_summary)),
        )
    }
}

private val LockAfterValues = listOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
