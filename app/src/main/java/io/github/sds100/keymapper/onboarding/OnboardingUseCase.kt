package io.github.sds100.keymapper.onboarding

import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.system.apps.PackageManagerAdapter
import io.github.sds100.keymapper.system.files.FileAdapter
import io.github.sds100.keymapper.system.inputmethod.KeyMapperImeHelper
import io.github.sds100.keymapper.util.PrefDelegate
import io.github.sds100.keymapper.util.VersionHelper
import io.github.sds100.keymapper.util.ifIsData
import kotlinx.coroutines.flow.*

/**
 * Created by sds100 on 14/02/21.
 */
class OnboardingUseCaseImpl(
    private val preferenceRepository: PreferenceRepository,
    private val packageManagerAdapter: PackageManagerAdapter,
    private val fileAdapter: FileAdapter
) : PreferenceRepository by preferenceRepository, OnboardingUseCase {

    override var shownAppIntro by PrefDelegate(Keys.shownAppIntro, false)

    override val showGuiKeyboardPrompt: Flow<Boolean> =
        preferenceRepository.get(Keys.acknowledgedGuiKeyboard)
            .map { acknowledged -> acknowledged == null || !acknowledged }
            .map { show ->
                if (show) {
                    packageManagerAdapter.installedPackages.value.ifIsData { installedPackages ->
                        if (installedPackages.any { it.packageName == KeyMapperImeHelper.KEY_MAPPER_GUI_IME_PACKAGE }) {
                            neverShowGuiKeyboardPromptsAgain()
                            return@map false
                        }
                    }
                }

                return@map show
            }

    override fun neverShowGuiKeyboardPromptsAgain() {
        preferenceRepository.set(Keys.acknowledgedGuiKeyboard, true)
    }

    override var approvedFingerprintFeaturePrompt by PrefDelegate(
        Keys.approvedFingerprintFeaturePrompt,
        false
    )

    override var shownParallelTriggerOrderExplanation by PrefDelegate(
        Keys.shownParallelTriggerOrderExplanation,
        false
    )
    override var shownSequenceTriggerExplanation by PrefDelegate(
        Keys.shownSequenceTriggerExplanation,
        false
    )

    override val showWhatsNew = get(Keys.lastInstalledVersionCodeHomeScreen)
        .map { it ?: -1 < Constants.VERSION_CODE }

    override fun showedWhatsNew() {
        set(Keys.lastInstalledVersionCodeHomeScreen, Constants.VERSION_CODE)
    }

    override fun getWhatsNewText(): String {
        return with(fileAdapter.openAsset("whats-new.txt").bufferedReader()) {
            readText()
        }
    }

    override val showFingerprintFeatureNotificationIfAvailable: Flow<Boolean> by lazy {
        combine(
            get(Keys.lastInstalledVersionCodeBackground).map { it ?: -1 },
            showWhatsNew,
            get(Keys.approvedFingerprintFeaturePrompt).map { it ?: false },
            get(Keys.shownAppIntro).map { it ?: false }
        ) { oldVersionCode, showWhatsNew, approvedPrompt, shownAppIntro ->
            //has the user opened the app and will have already seen that they can remap fingerprint gestures
            val handledUpdateInHomeScreen = !showWhatsNew

            oldVersionCode < VersionHelper.FINGERPRINT_GESTURES_MIN_VERSION
                && !handledUpdateInHomeScreen
                && !approvedPrompt
                && shownAppIntro
        }
    }

    override fun showedFingerprintFeatureNotificationIfAvailable() {
        set(Keys.lastInstalledVersionCodeBackground, Constants.VERSION_CODE)
    }

    override val showSetupChosenDevicesAgainNotification: Flow<Boolean> =
        get(Keys.approvedSetupChosenDevicesAgain).map { it ?: false }.map { approvedPreviously ->
            val bluetoothDevicesThatShowImePicker =
                get(stringSetPreferencesKey("pref_bluetooth_devices_show_ime_picker")).first()
                    ?: emptySet()

            val bluetoothDevicesThatChangeIme =
                get(stringSetPreferencesKey("pref_bluetooth_devices")).first() ?: emptySet()

            val previouslyChoseBluetoothDevices =
                bluetoothDevicesThatShowImePicker.isNotEmpty() || bluetoothDevicesThatChangeIme.isNotEmpty()

            return@map !approvedPreviously && previouslyChoseBluetoothDevices
        }

    override fun approvedSetupChosenDevicesAgainNotification() {
        set(Keys.approvedSetupChosenDevicesAgain, true)
    }

    override val showSetupChosenDevicesAgainAppIntro: Flow<Boolean> =
        get(Keys.approvedSetupChosenDevicesAgain).map { it ?: false }.map { approvedPreviously ->

            val bluetoothDevicesThatShowImePicker =
                get(stringSetPreferencesKey("pref_bluetooth_devices_show_ime_picker")).first()
                    ?: emptySet()

            val bluetoothDevicesThatChangeIme =
                get(stringSetPreferencesKey("pref_bluetooth_devices")).first()
                    ?: emptySet()

            val previouslyChoseBluetoothDevices =
                bluetoothDevicesThatShowImePicker.isNotEmpty() || bluetoothDevicesThatChangeIme.isNotEmpty()

            return@map !approvedPreviously && previouslyChoseBluetoothDevices
        }

    override fun approvedSetupChosenDevicesAgainAppIntro() {
        set(Keys.approvedSetupChosenDevicesAgain, true)
    }

    override val showQuickStartGuideHint: Flow<Boolean> = get(Keys.shownQuickStartGuideHint).map {
        if (it == null) {
            true
        } else {
            !it
        }
    }

    override fun shownQuickStartGuideHint() {
        preferenceRepository.set(Keys.shownQuickStartGuideHint, true)
    }
}

interface OnboardingUseCase {
    var shownAppIntro: Boolean

    val showGuiKeyboardPrompt: Flow<Boolean>
    fun neverShowGuiKeyboardPromptsAgain()

    var approvedFingerprintFeaturePrompt: Boolean
    var shownParallelTriggerOrderExplanation: Boolean
    var shownSequenceTriggerExplanation: Boolean

    val showFingerprintFeatureNotificationIfAvailable: Flow<Boolean>
    fun showedFingerprintFeatureNotificationIfAvailable()

    val showSetupChosenDevicesAgainNotification: Flow<Boolean>
    fun approvedSetupChosenDevicesAgainNotification()

    val showSetupChosenDevicesAgainAppIntro: Flow<Boolean>
    fun approvedSetupChosenDevicesAgainAppIntro()

    val showWhatsNew: Flow<Boolean>
    fun showedWhatsNew()
    fun getWhatsNewText(): String

    val showQuickStartGuideHint: Flow<Boolean>
    fun shownQuickStartGuideHint()
}