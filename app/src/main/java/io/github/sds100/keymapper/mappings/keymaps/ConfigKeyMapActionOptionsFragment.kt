package io.github.sds100.keymapper.mappings.keymaps

import androidx.navigation.navGraphViewModels
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.mappings.ConfigActionOptionsViewModel
import io.github.sds100.keymapper.mappings.OptionsBottomSheetFragment
import io.github.sds100.keymapper.util.Inject
import io.github.sds100.keymapper.util.str

/**
 * Created by sds100 on 12/04/2021.
 */
class ConfigKeyMapActionOptionsFragment : OptionsBottomSheetFragment() {

    private val configKeyMapViewModel: ConfigKeyMapViewModel by navGraphViewModels(R.id.nav_config_keymap) {
        Inject.configKeyMapViewModel(requireContext())
    }

    override val viewModel: ConfigActionOptionsViewModel<KeyMap, KeyMapAction>
        get() = configKeyMapViewModel.configActionOptionsViewModel

    override val url: String
        get() = str(R.string.url_keymap_action_options_guide)
}