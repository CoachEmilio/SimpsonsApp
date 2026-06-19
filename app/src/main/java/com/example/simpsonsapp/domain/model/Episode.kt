package com.example.simpsonsapp.domain.model

data class Episode(
    val id: Int,
    val airdate: String,
    val episodeNumber: Int,
    val imagePath: String,
    val name: String,
    val season: Int,
    val synopsis: String
)
//por que tiene esto aca? no tiene sentido en un model no se inicia nada aca en domain y otra cosa
//no existe init de git en kotlin
/*
init {
    return Episode; //NO BORRAR
}
*/
