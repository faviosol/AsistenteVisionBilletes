
---                                                                                                                                                                         # TemachVision
  # TemachVision
  
  App Android que detecta billetes peruanos en en tiempo real con la cámara, mediante                                                                                                                                                                            App Android que detecta billetes peruanos en tiempo real usando la cámara
  un modelo de Inteligencia Artificial entrenado específicamente para eso.

  La idea nació para ayudar a personas con discapacidad visual a identificar
  el valor de un billete sin depender de nadie más.

  Detecta S/ 10, S/ 20, S/ 50 y S/ 100 soles.

  ---

  ## Tecnologías

  - Kotlin 1.8.22
  - TensorFlow Lite 2.14
  - CameraX 1.3.0
  - Android Navigation Component 2.5.3
  - Material Design 3 1.9.0
  - Gradle 8.1.4

  ---

  ## Para correr el proyecto

  ### Android Studio

  Este proyecto se trabajó y probó con:

  **Android Studio Giraffe | 2022.3.1 Patch 4 — November 16, 2023**

  Si tienes otra versión instalada te recomiendo usar esta misma para
  evitar problemas de compatibilidad. Para descargarla:

  1. Entra a: https://developer.android.com/studio/archive?hl=en
  2. Acepta los términos y condiciones
  3. Busca con Ctrl+F: `Android Studio Giraffe | 2022.3.1 Patch 4 November 16, 2023`
  4. Descarga el instalador de Windows (1.1 GB)

  ### Dispositivo

  Necesitas un celular físico con Android 7.0 o superior.
  No se recomienda emulador.

  **Si tienes Xiaomi:**
  1. Ajustes → Acerca del teléfono → toca varias veces "Versión del SO"
  2. Ajustes → Ajustes adicionales → Opciones del desarrollador
  3. Activa: Depuración por USB, Instalar vía USB, Depuración USB (ajustes de seguridad)

  ### Pasos

  1. Abre Android Studio
  2. File → Open → selecciona la carpeta del proyecto
  3. Espera 5-10 minutos sin tocar nada mientras Gradle descarga todo
  4. Conecta tu celular por USB
  5. Y ejecúta

  > Si interrumpes a Gradle antes de que termine puede dejar la
  > configuración corrupta y tendrás que limpiar manualmente.

  ---

  ## En desarrollo

  - Respuesta por voz al detectar un billete
  - Mejoras en la precisión del modelo
  - Soporte para billetes en mal estado
  - Implementación del modo seguridad

  ---

  ## Autor

  Favio Solórzano — [@thetemach-S](https://github.com/thetemach-S)

  ---