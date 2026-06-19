# 🐛 SimpsonsApp — Análisis de errores

> Trabajo Práctico 2 — Parte Práctica  
> **Alumno:** Emilio Romero Quirino  
> **Comisión:** Jueves TN Montserrat  
> **Cátedra:** Desarrollo de Aplicaciones I — UADE 2026

---

## 📋 Consigna

El código del repo del profe tiene **10 errores**. Hay que identificarlos, abrir un issue en GitHub por cada uno explicando qué está mal y cómo se soluciona.

**Repo original:** https://github.com/ExBattou/SimpsonsApp

---

## 🎯 Los 10 errores encontrados

A continuación el detalle de cada uno: archivo, qué está mal, y cómo lo arreglo.

---

### Issue 1 — Código basura en `Episode.kt` que rompe la compilación

**Archivo:** `domain/model/Episode.kt`

**El problema:**

Al final del archivo hay este bloque suelto fuera de la `data class`:

```kotlin
init {
    return Episode; //NO BORRAR
}
```

Esto no es Kotlin. `init` solo va dentro de una clase, no afuera. Y `return Episode;` con punto y coma tampoco existe en Kotlin. El comentario "NO BORRAR" es la trampa: si lo dejás, la app no compila.

**Cómo lo arreglo:**

Borro todo el bloque. El archivo termina después del `)` de la `data class`.

---

### Issue 2 — Función con guión bajo que rompe el `override`

**Archivo:** `domain/repository/EpisodeRepository.kt`

**El problema:**

La interface declara:

```kotlin
fun get_episodes(): Flow<PagingData<Episode>>
```

Pero la implementación (`EpisodeRepositoryImpl`) usa:

```kotlin
override fun getEpisodes(): Flow<PagingData<Episode>>
```

Como los nombres no coinciden (`get_episodes` vs `getEpisodes`), el `override` no funciona y no compila. Además, en Kotlin no se usa snake_case para funciones.

**Cómo lo arreglo:**

Renombro el método en la interface a `getEpisodes()` y arreglo la llamada en `GetEpisodesUseCase.kt`.

---

### Issue 3 — Falta `.baseUrl()` en Retrofit y la app crashea

**Archivo:** `di/DataModule.kt`

**El problema:**

```kotlin
return Retrofit.Builder()
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

Retrofit obliga a pasarle un `.baseUrl(...)` antes del `.build()`. Si falta, apenas la app intenta inyectar la API, crashea con:

```
IllegalStateException: Base URL required.
```

**Cómo lo arreglo:**

Agrego la línea (ojo que tiene que terminar en `/`):

```kotlin
.baseUrl("https://thesimpsonsapi.com/api/")
```

---

### Issue 4 — URL completa hardcodeada en el endpoint

**Archivo:** `data/remote/EpisodeRemoteMediator.kt`

**El problema:**

```kotlin
@GET("https://thesimpsonsapi.com/api/episodes")
suspend fun getEpisodes(...)
```

Retrofit espera que en `@GET` vaya solo el path relativo (ej: `"episodes"`), no la URL completa. La URL base ya tiene que vivir en el `Retrofit.Builder()` (cosa que tampoco está, ver Issue 3).

Encima, la interface `SimpsonsApi` está pegada al final del archivo del `EpisodeRemoteMediator`. Son dos cosas distintas y deberían estar en archivos separados.

**Cómo lo arreglo:**

1. Muevo `SimpsonsApi` a un archivo propio: `data/remote/api/SimpsonsApi.kt`.
2. Dejo solo el path:

```kotlin
@GET("episodes")
suspend fun getEpisodes(@Query("page") page: Int): EpisodesResponse
```

---

### Issue 5 — Hay dos ViewModels para la misma pantalla

**Archivos:** `main/MainViewModel.kt` + `main/MainScreenViewModel.kt` + `data/DataRepository.kt`

**El problema:**

En `main/` hay dos ViewModels:

- `MainViewModel` → el que la pantalla realmente usa. Tiene Paging, seasons, todo bien armado con `@HiltViewModel`.
- `MainScreenViewModel` → un archivo suelto que nadie llama, sin `@HiltViewModel`, sin `@Inject`, que recibe un `DataRepository` que devuelve `Flow<List<String>>` con valores tipo `"Android"` (que no tiene nada que ver con los Simpsons).

Una pantalla = un ViewModel. El segundo sobra.

**Cómo lo arreglo:**

Borro `MainScreenViewModel.kt` y también `data/DataRepository.kt` (que se usaba solo desde ahí).

---

### Issue 6 — Llamada al ViewModel desde el cuerpo del Composable

**Archivo:** `main/MainScreen.kt`

**El problema:**

```kotlin
if (episodes.loadState.refresh is LoadState.NotLoading && seasons.isEmpty()) {
    viewModel.refreshSeasons()
}
```

Esto está suelto en el cuerpo del Composable. Compose redibuja muchas veces por segundo, así que `refreshSeasons()` se llama una y otra vez sin control.

**Cómo lo arreglo:**

Lo envuelvo en `LaunchedEffect` para que solo se ejecute cuando cambien las claves:

```kotlin
LaunchedEffect(episodes.loadState.refresh, seasons.isEmpty()) {
    if (episodes.loadState.refresh is LoadState.NotLoading && seasons.isEmpty()) {
        viewModel.refreshSeasons()
    }
}
```

---

### Issue 7 — El mapper `toDomain()` está pegado en el Repository

**Archivo:** `data/repository/EpisodeRepositoryImpl.kt`

**El problema:**

Al final del archivo, fuera de la clase, está la función:

```kotlin
fun EpisodeEntity.toDomain() = Episode(
    id = id,
    airdate = airdate,
    ...
)
```

El Repository ya tiene su trabajo: coordinar Room y la API. Mezclarle el mapper Entity → Dominio ensucia el archivo. Si más adelante hay 10 entidades, todos los mappers se acumulan acá.

**Cómo lo arreglo:**

Muevo la función a un archivo propio:

`data/local/mappers/EpisodeMapper.kt`

```kotlin
package com.example.simpsonsapp.data.local.mappers

fun EpisodeEntity.toDomain() = Episode(...)
```

Y agrego el import en `EpisodeRepositoryImpl.kt`.

---

### Issue 8 — `DetailViewModel` no distingue cargando / error / no encontrado

**Archivo:** `detail/DetailViewModel.kt`

**El problema:**

```kotlin
private val _episode = MutableStateFlow<Episode?>(null)
val episode: StateFlow<Episode?> = _episode.asStateFlow()
```

El estado es `Episode?`. Cuando vale `null` no sé si:

- Está cargando.
- Hubo un error.
- El episodio no existe.

La UI siempre muestra un loader, aunque haya fallado la red.

**Cómo lo arreglo:**

Uso una `sealed interface` con los estados explícitos (igual que el patrón Loading/Success/Error que vimos en clase):

```kotlin
sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(val episode: Episode) : DetailUiState
    data class Error(val message: String) : DetailUiState
    object NotFound : DetailUiState
}
```

Y la UI hace `when (uiState)` con los 4 casos.

---

### Issue 9 — Los logs HTTP están siempre activos, incluso en producción

**Archivo:** `di/DataModule.kt`

**El problema:**

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

Esto loguea TODO lo que pasa por la red: headers, body, tokens, datos del usuario. Está bueno cuando estás programando, pero si la app sale a producción así, cualquier persona que abra Logcat ve datos sensibles.

**Cómo lo arreglo:**

Lo activo solo en debug:

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}
```

---

### Issue 10 — Falta el `@OptIn` para `flatMapLatest`

**Archivo:** `main/MainViewModel.kt`

**El problema:**

```kotlin
val episodes: Flow<PagingData<Episode>> = _selectedSeason.flatMapLatest { season ->
    ...
}.cachedIn(viewModelScope)
```

`flatMapLatest` es un operador marcado como experimental (`@ExperimentalCoroutinesApi`). Si no se activa con `@OptIn`, el compilador tira warning (o error en configuraciones estrictas).

**Cómo lo arreglo:**

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(...) : ViewModel() {
```

---

## 📊 Resumen

| # | Archivo | Qué pasa |
|---|---|---|
| 1 | `Episode.kt` | Código basura que no compila |
| 2 | `EpisodeRepository.kt` | `get_episodes` no matchea con `getEpisodes` |
| 3 | `DataModule.kt` | Falta `.baseUrl()`, crashea |
| 4 | `EpisodeRemoteMediator.kt` | URL hardcodeada + interface mal ubicada |
| 5 | `main/` + `data/` | 2 ViewModels para una pantalla + repo mock |
| 6 | `MainScreen.kt` | Side effect sin `LaunchedEffect` |
| 7 | `EpisodeRepositoryImpl.kt` | Mapper pegado en el Repository |
| 8 | `DetailViewModel.kt` | Estado ambiguo con `Episode?` |
| 9 | `DataModule.kt` | Logs HTTP activos en producción |
| 10 | `MainViewModel.kt` | Falta `@OptIn` para `flatMapLatest` |

---