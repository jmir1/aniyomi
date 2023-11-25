package eu.kanade.presentation.history

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.util.ThemePreviews
import kotlin.random.Random

@Composable
fun HistoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: (Boolean) -> Unit,
    isManga: Boolean,
) {
    var removeEverything by remember { mutableStateOf(false) }

    AlertDialog(
        title = {
            Text(text = stringResource(R.string.action_remove))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val subtitle = if (isManga) {
                    R.string.dialog_with_checkbox_remove_description
                } else {
                    R.string.dialog_with_checkbox_remove_description_anime
                }
                Text(text = stringResource(subtitle))

                LabeledCheckbox(
                    label = stringResource(R.string.dialog_with_checkbox_reset),
                    checked = removeEverything,
                    onCheckedChange = { removeEverything = it },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete(removeEverything)
                onDismissRequest()
            }) {
                Text(text = stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun HistoryDeleteAllDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.action_remove_everything))
        },
        text = {
            Text(text = stringResource(R.string.clear_history_confirmation))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@ThemePreviews
@Composable
private fun HistoryDeleteDialogPreview() {
    TachiyomiTheme {
        HistoryDeleteDialog(
            onDismissRequest = {},
            onDelete = {},
            isManga = Random.nextBoolean(),
        )
    }
}
