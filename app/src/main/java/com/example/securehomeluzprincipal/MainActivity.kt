package com.example.securehomeluzprincipal

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.securehomeluzprincipal.ui.theme.SecureHomeLuzPrincipalTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Punto de entrada de la aplicación SecureHome.
 * Configura Jetpack Compose, permisos y la navegación entre pantallas.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitamos permisos de Bluetooth en tiempo de ejecución (Android 12+).
        ensureBluetoothPermissions()

        setContent {
            SecureHomeLuzPrincipalTheme {
                SecureHomeApp()
            }
        }
    }

    /**
     * Solicita permisos de Bluetooth (BLUETOOTH_SCAN y BLUETOOTH_CONNECT)
     * en dispositivos con Android 12 o superior.
     */
    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            val missing = permissions.filter { perm ->
                ContextCompat.checkSelfPermission(this, perm) !=
                        PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    100
                )
            }
        }
    }
}

/**
 * Constantes críticas centralizadas.
 * Aquí definimos:
 * - credenciales válidas para el login
 * - dirección MAC del módulo Bluetooth
 * - UUID del servicio serie (HC-05).
 */
object Constants {
    const val VALID_USERNAME = "admin"
    const val VALID_PASSWORD = "1234"

    // IMPORTANTE: cambia esta dirección MAC por la de TU módulo HC-05.
    // Por ahora es solo un ejemplo, si no coincide simplemente no conectará.
    const val BLUETOOTH_DEVICE_ADDRESS = "00:11:22:33:44:55"

    // UUID estándar para comunicación serie (SPP) con HC-05.
    const val BLUETOOTH_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB"
}

/**
 * Pantallas disponibles en la app.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
}

/**
 * Composable raíz de la aplicación.
 * Administra la navegación entre Login y Dashboard.
 */
@Composable
fun SecureHomeApp() {
    val navController: NavHostController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route
        ) {
            composable(route = Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            composable(route = Screen.Dashboard.route) {
                DashboardScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Dashboard.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pantalla de Login.
 * Primera pantalla de la app (StartDestination).
 * Valida credenciales y controla acceso al Dashboard.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "SecureHome",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Inicio de Sesión",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    // Validación de integridad de datos (campos no vacíos)
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Por favor completa usuario y contraseña."
                        return@Button
                    }

                    // Validación de credenciales (seguridad de acceso OT)
                    if (username == Constants.VALID_USERNAME &&
                        password == Constants.VALID_PASSWORD
                    ) {
                        errorMessage = null
                        onLoginSuccess()
                    } else {
                        errorMessage = "Credenciales incorrectas."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ingresar")
            }
        }
    }
}

/**
 * Controlador de Bluetooth.
 * Se encarga de conectar, desconectar y enviar comandos al Arduino
 * utilizando corrutinas para no bloquear el hilo principal.
 */
class BluetoothController(private val activity: Activity) {

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Intenta establecer conexión con el módulo HC-05.
     * onConnected: se llama en el hilo principal si la conexión fue exitosa.
     * onError: se llama en el hilo principal con un mensaje de error.
     */
    fun connect(
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onError("Bluetooth no está disponible en este dispositivo.")
            return
        }

        // Verificación básica de permisos en tiempo de ejecución
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnectPermission) {
                onError("Faltan permisos de Bluetooth (CONNECT).")
                return
            }
        }

        val device = try {
            adapter.getRemoteDevice(Constants.BLUETOOTH_DEVICE_ADDRESS)
        } catch (e: IllegalArgumentException) {
            onError("Dirección MAC del dispositivo Bluetooth inválida.")
            return
        }

        scope.launch {
            try {
                val uuid = UUID.fromString(Constants.BLUETOOTH_UUID_STRING)
                val tmpSocket = device.createRfcommSocketToServiceRecord(uuid)

                adapter.cancelDiscovery()
                tmpSocket.connect()
                socket = tmpSocket

                withContext(Dispatchers.Main) {
                    onConnected()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    onError("Error al conectar: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error inesperado al conectar: ${e.message}")
                }
            }
        }
    }

    /**
     * Cierra el socket Bluetooth si está abierto.
     */
    fun disconnect() {
        scope.launch {
            try {
                socket?.close()
                socket = null
            } catch (_: IOException) {
            }
        }
    }

    /**
     * Envía un comando de texto (por ejemplo "1" o "0") al Arduino.
     */
    fun sendCommand(
        command: String,
        onError: (String) -> Unit
    ) {
        val currentSocket = socket
        if (currentSocket == null) {
            onError("No hay conexión Bluetooth activa.")
            return
        }

        scope.launch {
            try {
                val out = currentSocket.outputStream
                out.write(command.toByteArray())
                out.flush()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    onError("Error al enviar comando: ${e.message}")
                }
            }
        }
    }
}

/**
 * Pantalla principal de control (Dashboard).
 * - Conectar / Desconectar Bluetooth
 * - Indicador de estado
 * - Botón para encender / apagar LED (envía '1' o '0')
 * - Cierre de sesión
 */
@Composable
fun DashboardScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val bluetoothController = remember { BluetoothController(activity) }

    var isConnected by remember { mutableStateOf(false) }
    var ledOn by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Panel de Control",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected) "Estado: Conectado" else "Estado: Desconectado",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!isConnected) {
                        // Intentar conectar
                        bluetoothController.connect(
                            onConnected = {
                                isConnected = true
                                statusMessage = "Conectado correctamente al dispositivo."
                            },
                            onError = { error ->
                                isConnected = false
                                statusMessage = error
                            }
                        )
                    } else {
                        // Desconectar
                        bluetoothController.disconnect()
                        isConnected = false
                        ledOn = false
                        statusMessage = "Conexión Bluetooth cerrada."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (!isConnected) "Conectar Bluetooth" else "Desconectar Bluetooth"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (!isConnected) {
                        statusMessage = "Conéctate primero al dispositivo Bluetooth."
                        return@Button
                    }

                    val command = if (!ledOn) "1" else "0"

                    bluetoothController.sendCommand(
                        command = command,
                        onError = { error ->
                            statusMessage = error
                        }
                    )

                    ledOn = !ledOn
                    statusMessage = if (ledOn) {
                        "LED encendido (se envió '1' al Arduino)."
                    } else {
                        "LED apagado (se envió '0' al Arduino)."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected
            ) {
                Text(text = if (ledOn) "Apagar LED" else "Encender LED")
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Cerrar sesión")
            }
        }
    }
}
