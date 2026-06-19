package com.example.simpsonsapp.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.simpsonsapp.data.local.AppDatabase
import com.example.simpsonsapp.data.local.entity.EpisodeEntity
import com.example.simpsonsapp.data.local.entity.RemoteKeyEntity
import com.example.simpsonsapp.data.remote.model.EpisodesResponse
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class EpisodeRemoteMediator(
    private val simpsonsApi: SimpsonsApi,
    private val appDatabase: AppDatabase
) : RemoteMediator<Int, EpisodeEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, EpisodeEntity>
    ): MediatorResult {
        return try {
            val page = when (loadType) {
                LoadType.REFRESH -> {
                    val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                    remoteKeys?.nextKey?.minus(1) ?: 1
                }
                LoadType.PREPEND -> {
                    val remoteKeys = getRemoteKeyForFirstItem(state)
                    val prevKey = remoteKeys?.prevKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    prevKey
                }
                LoadType.APPEND -> {
                    val remoteKeys = getRemoteKeyForLastItem(state)
                    val nextKey = remoteKeys?.nextKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    nextKey
                }
            }

            val response = simpsonsApi.getEpisodes(page = page)
            val episodes = response.results
            val endOfPaginationReached = episodes.isEmpty()

            appDatabase.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    appDatabase.remoteKeyDao().clearRemoteKeys()
                    appDatabase.episodeDao().clearAll()
                }
                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = episodes.map {
                    RemoteKeyEntity(episodeId = it.id, prevKey = prevKey, nextKey = nextKey)
                }
                appDatabase.remoteKeyDao().insertAll(keys)
                appDatabase.episodeDao().insertAll(episodes.map {
                    EpisodeEntity(
                        id = it.id,
                        airdate = it.airdate,
                        episodeNumber = it.episodeNumber,
                        imagePath = it.imagePath,
                        name = it.name,
                        season = it.season,
                        synopsis = it.synopsis
                    )
                })
            }
            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: IOException) {
            MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, EpisodeEntity>): RemoteKeyEntity? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { episode ->
                appDatabase.remoteKeyDao().remoteKeysEpisodeId(episode.id)
            }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, EpisodeEntity>): RemoteKeyEntity? {
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { episode ->
                appDatabase.remoteKeyDao().remoteKeysEpisodeId(episode.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, EpisodeEntity>): RemoteKeyEntity? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { episodeId ->
                appDatabase.remoteKeyDao().remoteKeysEpisodeId(episodeId)
            }
        }
    }
}

interface SimpsonsApi {
    //URL absoluta hardcodeada en @GET y SimpsonsApi mal ubicada
    //podroa ser encapsulado esto en otro lugar, esto aca como que rompe un poco con el patron SOLID
    @GET("https://thesimpsonsapi.com/api/episodes")
    suspend fun getEpisodes(
        @Query("page") page: Int
    ): EpisodesResponse
}
