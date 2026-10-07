package com.vitalia.smartwatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    val watchId = WatchIdentity.get(application)
    private val preferences = application.getSharedPreferences("vitalia", Application.MODE_PRIVATE)
    private val simulador = SimuladorSmartwatch(
        sink = FirebaseRestDataSink(watchId),
        nombre = preferences.getString("adultoMayor", "María López") ?: "María López"
    )

    val datos = simulador.datos

    init { simulador.iniciar(viewModelScope) }

    fun simularCaida() = simulador.simularCaida()
    fun simularFcAlta() = simulador.simularFcAlta()
    fun emergencia() = simulador.botonEmergencia()
    fun cancelarEmergencia() = simulador.cancelarEmergencia()
    fun alternarUbicacion() = simulador.alternarUbicacion()
    fun actualizarNombre(nombre: String) {
        val normalizedName = nombre.trim()
        if (normalizedName.isEmpty()) return
        preferences.edit().putString("adultoMayor", normalizedName).apply()
        simulador.actualizarNombre(normalizedName)
    }
    fun actualizarAcelerometro(x: Float, y: Float, z: Float) =
        simulador.actualizarAcelerometro(x, y, z)
    fun actualizarBateria(porcentaje: Int) = simulador.actualizarBateria(porcentaje)
    fun actualizarUbicacion(latitud: Double, longitud: Double) =
        simulador.actualizarUbicacion(latitud, longitud)

    override fun onCleared() {
        simulador.detener()
        super.onCleared()
    }
}
