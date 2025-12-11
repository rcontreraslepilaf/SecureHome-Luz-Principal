// =====================================================
//   SecureHome - Control de LED vía Bluetooth (HC-05)
//   Arduino UNO + LED + Resistencia 220 ohm
//
//   Este programa recibe comandos enviados desde una
//   aplicación móvil mediante un módulo Bluetooth HC-05.
//
//   Comandos esperados:
//       '1' → Encender LED
//       '0' → Apagar LED
//
//   Flujo de comunicación:
//   App Android → Bluetooth → HC-05 → Serial UART → Arduino
// =====================================================

// Pin donde se encuentra conectado el LED.
// Se usa el pin 13 porque la mayoría de las placas Arduino
// ya integran un LED en ese pin, pero puede cambiarse si se usa
// un LED externo (por ejemplo, pin 7).
int ledPin = 13;


void setup() {
  // Inicializa la comunicación serial a 9600 baudios.
  // Esta velocidad debe coincidir con la configuración del HC-05.
  Serial.begin(9600);

  // Configura el pin del LED como salida.
  pinMode(ledPin, OUTPUT);

  // Asegura que el LED inicie apagado al encender el Arduino.
  digitalWrite(ledPin, LOW);
}


void loop() {

  // Verifica si hay datos disponibles en el buffer serial.
  // Esto significa que el HC-05 envió un comando desde la app.
  if (Serial.available() > 0) {

    // Lee el carácter enviado por Bluetooth.
    // Puede ser '1' o '0', según lo enviado desde la APK.
    char cmd = Serial.read();

    // Si el comando recibido es '1':
    // → Encender LED
    if (cmd == '1') {
      digitalWrite(ledPin, HIGH);   // Enciende el LED físico
      Serial.println("LED_ON");     // Envía confirmación (opcional)
    }

    // Si el comando recibido es '0':
    // → Apagar LED
    else if (cmd == '0') {
      digitalWrite(ledPin, LOW);    // Apaga el LED físico
      Serial.println("LED_OFF");    // Envía confirmación (opcional)
    }

    // Si se recibiera cualquier otro carácter, simplemente se ignora.
    // (No hace falta manejarlo porque la app solo envía '1' o '0').
  }
}
