package com.vitalia.smartwatch

import android.graphics.Bitmap
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private val VerdeVitalia = Color(0xFF52E37E)
private val RojoEmergencia = Color(0xFFE53935)
private val AmarilloAlerta = Color(0xFFFFC107)

class MainActivity : ComponentActivity(), SensorEventListener {
    private val vm: WatchViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var locationUpdatesRegistered = false
    private val locationListener = LocationListener { location ->
        vm.actualizarUbicacion(location.latitude, location.longitude)
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            iniciarActualizacionesUbicacion()
        } else {
            Toast.makeText(this, "Permite la ubicación para sincronizar las coordenadas.", Toast.LENGTH_LONG).show()
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) vm.actualizarBateria(level * 100 / scale)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        setContent { MaterialTheme { PantallaReloj(vm) } }
    }

    override fun onResume() {
        super.onResume()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, batteryFilter)
        }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        iniciarActualizacionesUbicacion()
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        unregisterReceiver(batteryReceiver)
        if (locationUpdatesRegistered) {
            try {
                locationManager.removeUpdates(locationListener)
            } catch (_: SecurityException) {
                // El permiso pudo revocarse mientras la pantalla estaba abierta.
            }
            locationUpdatesRegistered = false
        }
        super.onPause()
    }

    fun solicitarUbicacion() {
        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            iniciarActualizacionesUbicacion()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun iniciarActualizacionesUbicacion() {
        if (locationUpdatesRegistered) return
        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val providers = listOfNotNull(
            if (hasFine) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER
        )
        val provider = providers.firstOrNull { locationManager.isProviderEnabled(it) }
        if (provider == null) {
            Toast.makeText(this, "Activa los servicios de ubicación del reloj.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            locationManager.getLastKnownLocation(provider)?.let { location ->
                vm.actualizarUbicacion(location.latitude, location.longitude)
            }
            locationManager.requestLocationUpdates(
                provider,
                15_000L,
                10f,
                locationListener,
                Looper.getMainLooper()
            )
            locationUpdatesRegistered = true
        } catch (_: SecurityException) {
            Toast.makeText(this, "No hay permiso para obtener la ubicación.", Toast.LENGTH_LONG).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(this, "El reloj no dispone de un proveedor de ubicación activo.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            vm.actualizarAcelerometro(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
fun PantallaReloj(vm: WatchViewModel) {
    val d by vm.datos.collectAsState()
    val context = LocalContext.current
    val activity = context as? MainActivity
    val listState = rememberScalingLazyListState()
    var mostrarQr by remember { mutableStateOf(false) }

    val colorEstado = when (d.estado) {
        "EMERGENCIA" -> RojoEmergencia
        "Alerta" -> AmarilloAlerta
        else -> VerdeVitalia
    }

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Text("Vitalia", color = VerdeVitalia, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            item { Text(d.adultoMayor, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            item {
                Chip(
                    onClick = {
                        val input = EditText(context).apply {
                            setText(d.adultoMayor)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                            setSingleLine(true)
                        }
                        AlertDialog.Builder(context)
                            .setTitle("Nombre del adulto mayor")
                            .setView(input)
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Guardar") { _, _ -> vm.actualizarNombre(input.text.toString()) }
                            .show()
                    },
                    label = { Text("Editar nombre") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            item {
                Text(d.estado, color = colorEstado, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            item { Dato("❤️ Frecuencia", "${d.frecuenciaCardiaca} bpm") }
            item { Dato("Acelerómetro", "%.1f, %.1f, %.1f".format(d.acelerometroX, d.acelerometroY, d.acelerometroZ)) }
            item { Dato("🚶 Movimiento", d.movimiento) }
            item { Dato("📍 Ubicación", d.ubicacion) }
            item {
                val coordenadas = if (d.ubicacionReal && d.latitud != null && d.longitud != null) {
                    "%.5f, %.5f".format(d.latitud, d.longitud)
                } else "Sin lectura de ubicación"
                Dato("Coordenadas", coordenadas)
            }
            item {
                Chip(
                    onClick = { activity?.solicitarUbicacion() },
                    label = { Text(if (d.ubicacionReal) "Actualizar ubicación" else "Activar ubicación") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            item { Dato("🔋 Batería", "${d.bateria}%") }
            item { Dato("Eventos", "${d.caidas} caídas · ${d.alertasLeves} alertas leves") }
            item { Dato("Horas activas", "%.2f h".format(d.horasActivas)) }

            item {
                Chip(
                    onClick = { mostrarQr = !mostrarQr },
                    label = { Text(if (mostrarQr) "Ocultar QR" else "Vincular con QR") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            if (mostrarQr) {
                item {
                    val qr = remember(vm.watchId) { generarQrBitmap("vitalia://pair/${vm.watchId}") }
                    Image(bitmap = qr.asImageBitmap(), contentDescription = "QR para vincular el reloj", modifier = Modifier.size(110.dp))
                }
                item { Dato("ID del reloj", vm.watchId.take(8)) }
            }

            item {
                Chip(
                    onClick = { if (d.estado == "EMERGENCIA") vm.cancelarEmergencia() else vm.emergencia() },
                    label = {
                        Text(if (d.estado == "EMERGENCIA") "Cancelar SOS" else "🚑 Emergencia (SOS)",
                            textAlign = TextAlign.Center)
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = RojoEmergencia)
                )
            }

            // Controles de simulación
            item { Text("Simulación", fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
            item { BotonSim("Simular impacto / caída") { vm.simularCaida() } }
            item { BotonSim("FC Alta") { vm.simularFcAlta() } }
            item { BotonSim("Salir de Casa/Entrar") { vm.alternarUbicacion() } }
        }
        TimeText()
    }
}

private fun generarQrBitmap(contenido: String): Bitmap {
    val matrix = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 256, 256)
    return Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}

@Composable
private fun Dato(titulo: String, valor: String) {
    Text("$titulo: $valor", fontSize = 12.sp, textAlign = TextAlign.Center)
}

@Composable
private fun BotonSim(texto: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(texto, fontSize = 12.sp) },
        colors = ChipDefaults.secondaryChipColors()
    )
}
