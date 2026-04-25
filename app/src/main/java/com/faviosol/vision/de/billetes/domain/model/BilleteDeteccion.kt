package com.faviosol.vision.de.billetes.domain.model

data class BilleteDeteccion(
    val etiqueta: String,
    val puntaje: Float,
    val tiempoInferencia: Long
)
