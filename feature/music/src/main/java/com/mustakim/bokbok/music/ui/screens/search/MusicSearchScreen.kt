@file:OptIn(ExperimentalMaterial3Api::class)

package com.mustakim.bokbok.music.ui.screens.search
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavController

import com.mustakim.bokbok.ui.shared.TopSearch

@Composable
fun MusicSearchScreen(
    navController: NavController,
    pureBlack: Boolean
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var active by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        TopSearch(
            query = query,
            onQueryChange = { query = it },
            onSearch = { searchQuery ->
                if (searchQuery.isNotBlank()) {
                    navController.navigate("search/$searchQuery")
                }
            },
            active = active,
            onActiveChange = { active = it },
            placeholder = {
                androidx.compose.material3.Text(stringResource(MusicR.string.search_yt_music))
            },
            leadingIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            focusRequester = focusRequester
        ) {
            OnlineSearchScreen(
                query = query.text,
                onQueryChange = { query = it },
                navController = navController,
                onSearch = { searchQuery ->
                    navController.navigate("search/$searchQuery")
                },
                onDismiss = { active = false },
                pureBlack = pureBlack
            )
        }
    }
}
