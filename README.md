2do Parcial - Parte Practica
Que se solicita:

El codigo tiene 10 errores. Recae en usted analizar que es un error dentro del codigo.
Los Alumnos tendran que forkear este repo como propio, hacer un issue desde Github con Comentarios refiriendo en que linea esta el error, y como se debe solucionar.
La respuesta sera con el link a ese Fork, y adentro deben estar los issues. Los profesores tenemos que poder ingresar al mismo. Recae en los alumnos asegurarse de que los profesores puedan ingresar.
Tambien pueden editar el Archivo Readme y poner los resultados dentro de sus propios forks.
https://github.com/ExBattou/SimpsonsApp

Emilio Romero Quirino

Issue 1
bloque `init { return Episode; }` inválido al final de Episode.kt rompe la compilación
domain/model/Episode.kt`
Líneas con el problema:
```kotlin
init {
    return Episode; //NO BORRAR
}
```
Hay un bloque `init { return Episode; }` fuera de cualquier clase, escrito en una sintaxis que no existe en Kotlin.
`init { }` solo es válido dentro de una clase, y `return Episode;` no es Kotlin válido
(ni el punto y coma ni devolver el nombre de la clase como valor).
El comentario "NO BORRAR" es una trampa intencional. Tal como está, el proyecto no compila.
Solución:
Eliminar todo el bloque. El archivo debe terminar después del cierre `)` de la `data class Episode`.


Issue 2
Error 2: método `get_episodes()` con snake_case en la interface rompe el override
Archivo: `domain/repository/EpisodeRepository.kt`
Línea con el problema:
```kotlin
fun get_episodes(): Flow<PagingData>
```
Kotlin usa camelCase para nombres de funciones, no snake_case.
Pero el problema crítico no es de estilo: la implementación `EpisodeRepositoryImpl` declara el método
como `getEpisodes()` (camelCase), y por lo tanto las firmas no coinciden. El `override` no aplica
y el código no compila. El `GetEpisodesUseCase` también llama a `repository.get_episodes()` que
tampoco resolvería al método correcto.
Solución:
Renombrar el método a camelCase en la interface:
```kotlin
fun getEpisodes(): Flow<PagingData>
```
Y actualizar la llamada en `GetEpisodesUseCase.kt`:
```kotlin
return repository.getEpisodes()
```

Issue 3
Error 3: falta `.baseUrl(...)` en DataModule.kt, Retrofit crashea en runtime
Archivo `di/DataModule.kt`
```kotlin
return Retrofit.Builder()
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(SimpsonsApi::class.java)
```
`Retrofit.Builder()` requiere obligatoriamente que se le pase una `baseUrl(...)` antes
del `.build()`. Sin ella, la app crashea en runtime al intentar inyectar `SimpsonsApi`:
IllegalStateException: Base URL required.
Solución:
Agregar la línea con la URL base (que debe terminar en `/`):
```kotlin
return Retrofit.Builder()
    .baseUrl("https://thesimpsonsapi.com/api/")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(SimpsonsApi::class.java)
```


Issue 4
Error 4: URL absoluta hardcodeada en @GET y SimpsonsApi mal ubicada
Archivo: `data/remote/EpisodeRemoteMediator.kt`
```kotlin
interface SimpsonsApi {
    @GET("https://thesimpsonsapi.com/api/episodes")
    suspend fun getEpisodes(
        @Query("page") page: Int
    ): EpisodesResponse
}
```
Dos problemas combinados:
1. Retrofit espera endpoints relativos a la `baseUrl` del cliente. Hardcodear la URL completa rompe el patrón, duplica configuración y mezcla responsabilidades.
2. La interface `SimpsonsApi` está dentro del archivo `EpisodeRemoteMediator.kt`. Son responsabilidades distintas: una define el contrato de red, la otra orquesta la paginación. Mezclarlas viola Single Responsibility Principle.

Solución:
1. Mover `SimpsonsApi` a un archivo propio: `data/remote/api/SimpsonsApi.kt`.
2. Usar endpoint relativo:
```kotlin
@GET("episodes")
suspend fun getEpisodes(
    @Query("page") page: Int
): EpisodesResponse
```

Issue 5
2 ViewModels para la misma pantalla y DataRepository genérico sin uso real
Archivos: `main/MainViewModel.kt`, `main/MainScreenViewModel.kt`, `data/DataRepository.kt`

Existen dos ViewModels para `MainScreen` sin que quede claro cuál es el real y cuál es legacy.
El que la pantalla efectivamente usa es `MainViewModel`
(maneja Paging, seasons, etc., y está bien construido con `@HiltViewModel`).
`MainScreenViewModel` por su parte:
- No tiene `@HiltViewModel` ni `@Inject constructor`, por lo que no es inyectable.
- Recibe un `DataRepository` genérico que expone `Flow<List<String>>` con valores como `"Android"`, que no representa nada del dominio de los Simpsons.
- Nunca se referencia desde ninguna pantalla.
Tener dos ViewModels para una sola pantalla rompe la regla "una pantalla, un ViewModel" y confunde la arquitectura.

Solución:
Eliminar los archivos:
- `main/MainScreenViewModel.kt`
- `data/DataRepository.kt`
El `MainViewModel` existente cubre todo lo que la pantalla necesita.


Issue 6
side effect llamado directamente en el cuerpo del Composable
Archivo: `main/MainScreen.kt`

```kotlin
if (episodes.loadState.refresh is LoadState.NotLoading && seasons.isEmpty()) {
    viewModel.refreshSeasons()
}
```
Compose puede redibujar el Composable muchas veces por segundo. Llamar a `viewModel.refreshSeasons()` directamente en el cuerpo significa que el método se invoca repetidamente cada vez que el estado cambie.
Es un anti-patrón conocido en Compose: las llamadas a ViewModels (side effects) deben hacerse en bloques controlados como `LaunchedEffect`.
Además, la lógica de "si los seasons están vacíos, refrescá" es una decisión de negocio que debería vivir en el ViewModel, no en la UI.
El Composable solo debería mostrar estado y notificar eventos.

Solución:

Envolver la llamada en un `LaunchedEffect` con claves relevantes:

```kotlin
LaunchedEffect(episodes.loadState.refresh, seasons.isEmpty()) {
    if (episodes.loadState.refresh is LoadState.NotLoading && seasons.isEmpty()) {
        viewModel.refreshSeasons()
    }
}
```
Idealmente, mover esta lógica al `init` del ViewModel o a un método que la UI solo invoque una vez.

Issue 7
Error 7: función toDomain() mezclada en el archivo del Repository, viola SRP
Archivo: `data/repository/EpisodeRepositoryImpl.kt`
Líneas con el problema:

Al final del archivo, fuera de la clase:

```kotlin
fun EpisodeEntity.toDomain() = Episode(
    id = id,
    airdate = airdate,
    episodeNumber = episodeNumber,
    imagePath = imagePath,
    name = name,
    season = season,
    synopsis = synopsis
)
```

**Por qué está mal:**

La transformación de `EpisodeEntity` (capa de datos) a `Episode` (capa de dominio) no es responsabilidad del repositorio. El repositorio coordina fuentes de datos; el mapeo entre capas es responsabilidad de un mapper dedicado.

Mezclar el mapper en el archivo del Repository:
- Viola Single Responsibility Principle (la clase tiene dos motivos para cambiar).
- Dificulta la testabilidad del mapeo de forma aislada.
- Cuando crezca el proyecto, este archivo se va a volver inmanejable.

**Solución:**

Mover la función a un archivo dedicado de mappers:
`data/local/mappers/EpisodeMapper.kt`

```kotlin
package com.example.simpsonsapp.data.local.mappers

import com.example.simpsonsapp.data.local.entity.EpisodeEntity
import com.example.simpsonsapp.domain.model.Episode

fun EpisodeEntity.toDomain() = Episode(
    id = id,
    airdate = airdate,
    episodeNumber = episodeNumber,
    imagePath = imagePath,
    name = name,
    season = season,
    synopsis = synopsis
)
```

Y agregar el import correspondiente en `EpisodeRepositoryImpl.kt`.

Issue 8
Título:
Error 8: DetailViewModel usa Episode? en vez de sealed class de estados
Cuerpo:
markdown**Archivo:** `detail/DetailViewModel.kt`

**Líneas con el problema:**

```kotlin
private val _episode = MutableStateFlow(null)
val episode: StateFlow = _episode.asStateFlow()
```

**Por qué está mal:**

Usar `Episode?` como estado de UI es ambiguo: un valor `null` puede significar tres cosas distintas e indistinguibles:

- La pantalla está cargando.
- Hubo un error al obtener el episodio.
- El episodio no existe en el backend.

La UI no puede diferenciar entre estos casos y termina mostrando siempre un loader (como hace hoy con `if (episode != null) ... else CircularProgressIndicator()`), aunque en realidad haya habido un error.

**Solución:**

Modelar los estados explícitamente con una `sealed class`, igual que se hace en `MainScreenViewModel` (que aunque tenga otros problemas, sí usa este patrón correctamente):

```kotlin
sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Success(val episode: Episode) : DetailUiState
    data class Error(val message: String) : DetailUiState
    object NotFound : DetailUiState
}
```

Y en el ViewModel:

```kotlin
private val _uiState = MutableStateFlow(DetailUiState.Loading)
val uiState: StateFlow = _uiState.asStateFlow()

private fun loadEpisodeDetail(id: Int) {
    viewModelScope.launch {
        try {
            val episode = getEpisodeDetailUseCase(id)
            _uiState.value = if (episode != null) DetailUiState.Success(episode) else DetailUiState.NotFound
        } catch (e: Exception) {
            _uiState.value = DetailUiState.Error(e.message ?: "Error desconocido")
        }
    }
}
```

La UI usa `when` sobre el estado y muestra lo correcto en cada caso.

Issue 9
Título:
Error 9: HttpLoggingInterceptor en nivel BODY sin restricción de entorno
Cuerpo:
markdown**Archivo:** `di/DataModule.kt`

**Líneas con el problema:**

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

**Por qué está mal:**

`HttpLoggingInterceptor.Level.BODY` loguea los headers y el body completo de cada request y response en Logcat. Esto es útil en desarrollo, pero en una build de producción:

- Expone información sensible (tokens, datos de usuarios, estructura de la API).
- Degrada el rendimiento porque serializar y loguear todo el body tiene costo.
- Llena Logcat con ruido innecesario.

El interceptor debe activarse solo en builds de debug.

**Solución:**

Condicionar el nivel del interceptor según el tipo de build:

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}
```

Esto requiere que en `app/build.gradle.kts` esté activado `buildConfig = true` dentro de `buildFeatures { }`, lo cual es estándar.

Issue 10
Título:
Error 10: MainViewModel usa flatMapLatest sin @OptIn(ExperimentalCoroutinesApi::class)
Cuerpo:
markdown**Archivo:** `main/MainViewModel.kt`

**Líneas con el problema:**

```kotlin
val episodes: Flow<PagingData> = _selectedSeason.flatMapLatest { season ->
    if (season == null) {
        getEpisodesUseCase()
    } else {
        getEpisodesBySeasonUseCase(season)
    }
}.cachedIn(viewModelScope)
```

**Por qué está mal:**

`flatMapLatest` es un operador de `kotlinx.coroutines.flow` marcado como `@ExperimentalCoroutinesApi`. Para usarlo sin warnings de "experimental API usage" (o sin errores en configuraciones estrictas), hay que activar el opt-in explícitamente.

**Solución:**

Agregar el import y la anotación sobre la clase:

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    ...
) : ViewModel() {
```