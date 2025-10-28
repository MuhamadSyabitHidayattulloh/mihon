
package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import tachiyomi.presentation.core.components.material.Scaffold
import androidx.compose.material3.TopAppBar
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.presentation.core.screen.DeviceScreenSpec
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

object SettingsTranslationsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val scope = rememberCoroutineScope()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_category_translations),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            content = { contentPadding ->
                val downloadNewChapters by libraryPreferences.downloadNewChapters().collectAsState()
                val items = remember(downloadNewChapters) {
                    listOf(
                        Preference.PreferenceGroup(
                            title = stringResource(MR.strings.pref_category_translations),
                            preferenceItems = listOf(
                                Preference.PreferenceItem.Switch(
                                    pref = libraryPreferences.translateOnDownload(),
                                    title = stringResource(MR.strings.pref_enable_translations),
                                ),
                                Preference.PreferenceItem.ListPreference(
                                    pref = libraryPreferences.translateFrom(),
                                    title = stringResource(MR.strings.pref_translate_from),
                                    entries = mapOf(
                                        "en" to "English or latin script",
                                        "zh" to "Chinese",
                                        "ja" to "Japanese",
                                        "ko" to "Korean",
                                    ),
                                ),
                                Preference.PreferenceItem.ListPreference(
                                    pref = libraryPreferences.translateTo(),
                                    title = stringResource(MR.strings.pref_translate_to),
                                    entries = mapOf(
                                        "af" to "Afrikaans",
                                        "sq" to "Albanian",
                                        "ca" to "Catalan",
                                        "zh" to "Chinese",
                                        "hr" to "Croatian",
                                        "cs" to "Czech",
                                        "da" to "Danish",
                                        "nl" to "Dutch",
                                        "en" to "English",
                                        "et" to "Estonian",
                                        "fi" to "Filipino",
                                        "fr" to "Finnish",
                                        "de" to "German",
                                        "hu" to "Hungarian",
                                        "is" to "Icelandic",
                                        "id" to "Indonesian",
                                        "it" to "Italian",
                                        "ja" to "Japanese",
                                        "ko" to "Korean",
                                        "lv" to "Latvian",
                                        "lt" to "Lithuanian",
                                        "ms" to "Malay",
                                        "no" to "Norwegian",
                                        "pl" to "Polish",
                                        "pt" to "Portuguese",
                                        "ro" to "Romanian",
                                        "sr" to "Serbian (Latin)",
                                        "sk" to "Slovak",
                                        "sl" to "Slovenian",
                                        "es" to "Spanish",
                                        "sv" to "Swedish",
                                        "tr" to "Turkish",
                                        "vi" to "Vietnamese",
                                    ),
                                ),
                                Preference.PreferenceItem.ListPreference(
                                    pref = libraryPreferences.translateEngine(),
                                    title = stringResource(MR.strings.pref_translate_engine),
                                    entries = mapOf(
                                        "ml-kit" to "ML-Kit",
                                        "google-translate" to "Google Translate",
                                    ),
                                ),
                            ),
                        ),
                    )
                }
                PreferenceScreen(
                    items = items,
                    contentPadding = contentPadding,
                )
            }
        )
    }
}
