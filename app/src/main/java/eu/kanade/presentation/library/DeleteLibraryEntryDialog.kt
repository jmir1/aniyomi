package eu.kanade.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize

import tachiyomi.core.preference.CheckboxState
import tachiyomi.presentation.core.components.LabeledCheckbox

@Composable
fun DeleteLibraryEntryDialog(
    containsLocalEntry: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit,
    isManga: Boolean,
) {
    var list by remember {
        mutableStateOf(
            buildList<CheckboxState.State<StringResource>> {
                val checkbox1 = if (isManga) MR.strings.manga_from_library else MR.strings.anime_from_library
                add(CheckboxState.State.None(checkbox1))
                if (!containsLocalEntry) {
                    val checkbox2 = if (isManga) MR.strings.downloaded_chapters else MR.strings.downloaded_episodes
                    add(CheckboxState.State.None(checkbox2))
                }
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = localize(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                enabled = list.any { it.isChecked },
                onClick = {
                    onDismissRequest()
                    onConfirm(
                        list[0].isChecked,
                        list.getOrElse(1) { CheckboxState.State.None(0) }.isChecked,
                    )
                },
            ) {
                Text(text = localize(MR.strings.action_ok))
            }
        },
        title = {
            Text(text = localize(MR.strings.action_remove))
        },
        text = {
            Column {
                list.forEach { state ->
                    LabeledCheckbox(
                        label = localize(state.value),
                        checked = state.isChecked,
                        onCheckedChange = {
                            val index = list.indexOf(state)
                            if (index != -1) {
                                val mutableList = list.toMutableList()
                                mutableList[index] = state.next() as CheckboxState.State<StringResource>
                                list = mutableList.toList()
                            }
                        },
                    )
                }
            }
        },
    )
}
