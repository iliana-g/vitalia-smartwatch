package com.vitalia.smartwatch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

/** Simula los sensores de un smartwatch y publica un WatchData cada 2 segundos. */
class SimuladorSmartwatch(
    private val sink: DataSink,
    private var nombre: String = "María López"
) {
    companion object {
        const val INTERVALO_MS = 2000L
        const val UMBRAL_IMPACTO_M_S2 = 22f
    }

    private var fc = 74
    private var bateria = 100.0
    private var bateriaDelSistema = false
    private var caidas = 0
    private var alertasLeves = 0
    private var horasActivas = 0.0
    private var enCasa = true
    private var movimiento = "Normal"
    private var acelerometroX = 0f
    private var acelerometroY = 0f
    private var acelerometroZ = 0f
    private var caidaDetectada = false
    private var ultimaCaidaMs = 0L
    private var ticksFcAlta = 0
    private var ticksCaida = 0
    private var sosActivo = false
    private var latitud: Double? = null
    private var longitud: Double? = null
    private var ubicacionTimestamp: Long? = null
    private var fcAltaNotificada = false
    private var bateriaBajaNotificada = false
    private val alertas = mutableListOf<Alerta>()
    private var job: Job? = null

    private val _datos = MutableStateFlow(construir())
    val datos: StateFlow<WatchData> = _datos.asStateFlow()

    fun iniciar(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(INTERVALO_MS)
                tick()
            }
        }
    }

    fun detener() { job?.cancel() }

    // ---------- Acciones manuales (botones del reloj) ----------

    fun simularCaida() {
        acelerometroX = 0f
        acelerometroY = 0f
        acelerometroZ = UMBRAL_IMPACTO_M_S2 + 4f
        registrarCaida()
    }

    fun simularFcAlta() {
        ticksFcAlta = 5
        fc = 120
        publicar(emergencia = false)
    }

    fun botonEmergencia() {
        sosActivo = true
        agregarAlerta("SOS", "Botón de emergencia presionado")
        publicar(emergencia = true)
    }

    fun cancelarEmergencia() {
        sosActivo = false
        publicar(emergencia = false)
    }

    fun alternarUbicacion() {
        enCasa = !enCasa
        if (!enCasa) {
            alertasLeves++
            agregarAlerta("FUERA_DE_CASA", "Salió de casa")
        }
        publicar(emergencia = false)
    }

    fun actualizarAcelerometro(x: Float, y: Float, z: Float) {
        acelerometroX = x
        acelerometroY = y
        acelerometroZ = z
        val magnitud = sqrt(x * x + y * y + z * z)
        val ahora = System.currentTimeMillis()
        if (magnitud >= UMBRAL_IMPACTO_M_S2 && ahora - ultimaCaidaMs >= 5000L) {
            registrarCaida()
        }
    }

    fun actualizarBateria(porcentaje: Int) {
        bateria = porcentaje.coerceIn(0, 100).toDouble()
        bateriaDelSistema = true
        publicar(emergencia = false)
    }

    fun actualizarNombre(nombre: String) {
        val nombreNormalizado = nombre.trim()
        if (nombreNormalizado.isEmpty()) return
        this.nombre = nombreNormalizado
        publicar(emergencia = false)
    }

    fun actualizarUbicacion(latitud: Double, longitud: Double) {
        if (!latitud.isFinite() || !longitud.isFinite()) return
        this.latitud = latitud
        this.longitud = longitud
        ubicacionTimestamp = System.currentTimeMillis()
        publicar(emergencia = false)
    }

    private fun registrarCaida() {
        ultimaCaidaMs = System.currentTimeMillis()
        ticksCaida = 3
        caidaDetectada = true
        caidas++
        agregarAlerta("CAIDA", "Impacto fuerte detectado por el acelerómetro")
        publicar(emergencia = true)
    }

    // ---------- Lógica de simulación ----------

    private fun tick() {
        // Frecuencia cardíaca
        fc = if (ticksFcAlta > 0) {
            ticksFcAlta--
            Random.nextInt(115, 131)
        } else {
            (fc + Random.nextInt(-3, 4) + (74 - fc) / 8).coerceIn(58, 98)
        }

        // Movimiento
        movimiento = when {
            ticksCaida > 0 -> { ticksCaida--; "Caída detectada" }
            else -> when (Random.nextInt(100)) {
                in 0..64 -> "Normal"
                in 65..84 -> "Reposo"
                else -> "Activo"
            }
        }
        if (ticksCaida == 0) caidaDetectada = false
        if (movimiento == "Normal" || movimiento == "Activo") {
            horasActivas += INTERVALO_MS / 3_600_000.0
        }

        // Batería: baja ~1% por minuto (30 ticks)
        if (!bateriaDelSistema) {
            bateria = (bateria - 1.0 / 30.0).coerceAtLeast(0.0)
        }
        if (bateria <= 20 && !bateriaBajaNotificada) {
            bateriaBajaNotificada = true
            alertasLeves++
            agregarAlerta("BATERIA_BAJA", "Batería del reloj baja (${bateria.toInt()}%)")
        }

        // Alertas por frecuencia cardíaca
        if ((fc > 110 || fc < 50) && !fcAltaNotificada) {
            fcAltaNotificada = true
            alertasLeves++
            agregarAlerta(if (fc > 110) "FC_ALTA" else "FC_BAJA", "Frecuencia cardíaca anormal: $fc bpm")
        } else if (fc in 50..110) {
            fcAltaNotificada = false
        }

        publicar(emergencia = false)
    }

    private fun agregarAlerta(tipo: String, mensaje: String) {
        alertas.add(0, Alerta(tipo, mensaje, System.currentTimeMillis()))
        if (alertas.size > 20) alertas.removeAt(alertas.lastIndex)
    }

    private fun construir(): WatchData {
        val estado = when {
            sosActivo || movimiento == "Caída detectada" -> "EMERGENCIA"
            fc > 110 || fc < 50 -> "Alerta"
            else -> "Todo normal"
        }
        return WatchData(
            adultoMayor = nombre,
            estado = estado,
            frecuenciaCardiaca = fc,
            acelerometroX = acelerometroX,
            acelerometroY = acelerometroY,
            acelerometroZ = acelerometroZ,
            caidaDetectada = caidaDetectada,
            movimiento = movimiento,
            ubicacion = if (latitud != null && longitud != null) "Ubicación activa"
                else if (enCasa) "En casa (demo)" else "Fuera de casa (demo)",
            latitud = latitud,
            longitud = longitud,
            ubicacionReal = latitud != null && longitud != null,
            ubicacionTimestamp = ubicacionTimestamp,
            bateria = bateria.toInt(),
            caidas = caidas,
            alertasLeves = alertasLeves,
            horasActivas = Math.round(horasActivas * 100) / 100.0,
            alertas = alertas.toList(),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun publicar(emergencia: Boolean) {
        val d = construir()
        _datos.value = d
        sink.enviar(d.toJson(), emergencia || d.estado == "EMERGENCIA")
    }
}
