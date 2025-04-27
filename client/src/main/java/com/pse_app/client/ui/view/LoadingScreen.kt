package com.pse_app.client.ui.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Displays a loading spinner.
 */
@Composable
fun LoadingScreen(
    modifier: Modifier,
    loadingText: String,
    onRetry: () -> Unit,
    errorText: String,
    showRetryOnError: BaseViewModel?,
    showRetryDelay: Duration? = Duration.ofSeconds(2),
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    var state by rememberSaveable { mutableStateOf(Failures.NONE) }
    LaunchedEffect(state) {
        if (state == Failures.NONE && showRetryDelay != null) {
            delay(showRetryDelay.toMillis())
            if (state == Failures.NONE)
                state = Failures.TIMEOUT
        }
    }
    LaunchedEffect(showRetryOnError) {
        showRetryOnError?.errors?.collect {
            state = Failures.ERORRED
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    val transition = rememberInfiniteTransition(label = "Dots Transition")
    val visibleDotsCount = transition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart
        ),
    )

    when (state) {
        Failures.NONE, Failures.TIMEOUT -> Text(
            text = loadingText + ".".repeat(visibleDotsCount.value),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(.9f),
        )

        Failures.ERORRED -> Text(
            text = errorText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(.9f),
        )
    }


    Spacer(modifier = Modifier.height(10.dp))

    if (state == Failures.ERORRED || state == Failures.TIMEOUT) Button(
        onClick = {
            state = Failures.NONE
            onRetry()
        }
    ) {
        Text(text = stringResource(R.string.retry_button_text))
    }

    Spacer(modifier = Modifier.weight(1f))
}

@Serializable
private enum class Failures {
    TIMEOUT, ERORRED, NONE
}
