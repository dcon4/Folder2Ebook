package com.folderpub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.folderpub.debug.DebugLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val verboseEnabled by DebugLogger.verboseEnabledFlow.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        contentDescription = "Back"
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Debugging",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Verbose logging",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = verboseEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            DebugLogger.setVerboseEnabled(context, enabled)
                        }
                    },
                    contentDescription = if (verboseEnabled) "Disable verbose logging" else "Enable verbose logging"
                )
            }

            Text(
                text = "Verbose logs provide detailed information for debugging. " +
                        "Enable only when troubleshooting, as it uses more battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "FolderPub v1.0",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Throw files into a folder and make an ebook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
