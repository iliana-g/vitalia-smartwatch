package com.vitalia.smartwatch

import org.json.JSONArray
import org.json.JSONObject

/** Una alerta generada por el reloj. */
data class Alerta(
    val tipo: String,      // "CAIDA", "FC_ALTA", "FC_BAJA", "SOS", "BATERIA_BAJA", "FUERA_DE_CASA"
    val mensaje: String,
    val timestamp: Long
)

/**
 * Paquete de datos que el reloj genera. Es lo que tu compañero
 * debe enviar a la app React Native (usa toJson()).
 */
data class WatchData(
    val adultoMayor: String,
    val estado: String,            // "Todo normal" | "Alerta" | "EMERGENCIA"
    val frecuenciaCardiaca: Int,   // bpm
    val acelerometroX: Float,
    val acelerometroY: Float,
    val acelerometroZ: Float,
    val caidaDetectada: Boolean,
    val movimiento: String,        // "Normal" | "Reposo" | "Activo" | "Caída detectada"
    val ubicacion: String,         // GPS activo o estado de ubicación de demo
    val latitud: Double?,
    val longitud: Double?,
    val ubicacionReal: Boolean,
    val ubicacionTimestamp: Long?,
    val bateria: Int,              // 0..100
    val caidas: Int,
    val alertasLeves: Int,
    val horasActivas: Double,
    val alertas: List<Alerta>,
    val timestamp: Long
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("adultoMayor", adultoMayor)
        o.put("estado", estado)
        o.put("frecuenciaCardiaca", frecuenciaCardiaca)
        o.put("acelerometroX", acelerometroX.toDouble())
        o.put("acelerometroY", acelerometroY.toDouble())
        o.put("acelerometroZ", acelerometroZ.toDouble())
        o.put("caidaDetectada", caidaDetectada)
        o.put("movimiento", movimiento)
        o.put("ubicacion", ubicacion)
        if (latitud != null && longitud != null) {
            o.put("latitud", latitud)
            o.put("longitud", longitud)
            o.put("ubicacionTimestamp", ubicacionTimestamp)
        }
        o.put("ubicacionReal", ubicacionReal)
        o.put("bateria", bateria)
        o.put("caidas", caidas)
        o.put("alertasLeves", alertasLeves)
        o.put("horasActivas", horasActivas)
        o.put("timestamp", timestamp)
        val arr = JSONArray()
        alertas.forEach {
            arr.put(JSONObject().apply {
                put("tipo", it.tipo)
                put("mensaje", it.mensaje)
                put("timestamp", it.timestamp)
            })
        }
        o.put("alertas", arr)
        return o.toString()
    }
}
