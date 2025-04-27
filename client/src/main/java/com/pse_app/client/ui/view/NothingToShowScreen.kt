package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pse_app.client.R

/**
 * View for displaying an empty list.
 */
@Composable
fun NothingToShowScreen(
    modifier: Modifier,
    header: String,
    subtext: String,
    subContent: @Composable (() -> Unit)? = null,
    goBack: (() -> Unit)? = null,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Spacer(modifier = Modifier.weight(1f))

    Text(
        header,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(.9f),
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        subtext,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(.9f),
    )

    if (goBack != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = goBack,
            shape = MaterialTheme.shapes.small
        ) { Text(stringResource(R.string.back)) }
    }
    if (subContent != null) {
        Spacer(modifier = Modifier.height(10.dp))
        subContent()
    }

    Spacer(modifier = Modifier.weight(1f))
}
