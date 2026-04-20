package com.faviosol.vision.de.billetes

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.faviosol.vision.de.billetes.databinding.ActivityMainBinding

/**
 * Punto de entrada principal de la aplicación.
 *
 * Sigue el patrón Single Activity: una sola Activity que actúa como
 * contenedor, y toda la lógica de pantallas se implementa en Fragments.
 */
class MainActivity : AppCompatActivity() {

    // Binding generado automáticamente a partir de activity_main.xml
    private lateinit var activityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Solución temporal para un bug de fuga de memoria en Android 10 (Q)
            // con IRequestFinishCallback$Stub. Ver: issuetracker.google.com/issues/139738913
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}
