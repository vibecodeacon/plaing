package dev.plaing.runtime.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Standard heading component.
 */
@Composable
fun PlaingHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 16.dp),
    )
}

/**
 * Standard text input component.
 */
@Composable
fun PlaingInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isSecret: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
    )
}

/**
 * Standard button component.
 */
@Composable
fun PlaingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(text)
    }
}

/**
 * Standard text display component.
 */
@Composable
fun PlaingText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 2.dp),
    )
}

/**
 * List item card for displaying entity data.
 */
@Composable
fun PlaingListItem(
    fields: Map<String, String>,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            var first = true
            for ((_, value) in fields) {
                if (first) {
                    Text(
                        text = value,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    first = false
                } else {
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Alert/notification display.
 */
@Composable
fun PlaingAlert(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message != null) {
        Card(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
