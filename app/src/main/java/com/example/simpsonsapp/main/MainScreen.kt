package com.example.simpsonsapp.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.simpsonsapp.components.EpisodeCard
import com.example.simpsonsapp.components.SeasonSelector
import kotlinx.coroutines.launch


//por que hay antipatron aca en compose
// no deberia tener logica de negocio aqui
// aca solo dberia tener screen, la logica deberia ser refactorizado en viewmodel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val seasons by viewModel.availableSeasons.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val episodes = viewModel.episodes.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Whenever Paging state changes to not loading, we can refresh seasons from DB
    if (episodes.loadState.refresh is LoadState.NotLoading && seasons.isEmpty()) {
        viewModel.refreshSeasons()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("The Simpsons Episodes") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Season Selector
            SeasonSelector(
                seasons = seasons,
                selectedSeason = selectedSeason,
                onSeasonSelected = { 
                    viewModel.onSeasonSelected(it)
                    coroutineScope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Episode Selector (Scrolls the main list to the episode)
            // Note: Since we are using Paging3, we don't have the full list of episodes in memory
            // at once unless they are loaded. We can provide a generic 1..25 selector.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Episode:",
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                (1..25).forEach { epNum ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                // Find the index of the episode with this number in the loaded items
                                // Since PagingItems doesn't provide a direct indexOf, we approximate
                                // or search through the snapshot.
                                val snapshot = episodes.itemSnapshotList
                                val index = snapshot.indexOfFirst { it?.episodeNumber == epNum }
                                if (index != -1) {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        },
                        label = { Text("$epNum") },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main List
            Box(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(count = episodes.itemCount) { index ->
                        val episode = episodes[index]
                        if (episode != null) {
                            EpisodeCard(
                                episode = episode,
                                onClick = { onNavigateToDetail(episode.id) }
                            )
                        }
                    }
                    
                    if (episodes.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                if (episodes.loadState.refresh is LoadState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
