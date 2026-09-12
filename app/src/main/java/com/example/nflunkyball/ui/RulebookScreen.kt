package com.example.nflunkyball.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.nflunkyball.ui.shared.BackTopBar
import com.example.nflunkyball.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulebookScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            BackTopBar(title = "Rulebook", onBack = onBack)
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Der Schiedsrichter hat immer Recht.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
