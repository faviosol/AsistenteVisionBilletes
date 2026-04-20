package com.faviosol.vision.de.billetes.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.faviosol.vision.de.billetes.R
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/**
 * Fragment cuya unica responsabilidad es solicitar el permiso de camara al usuario.
 *
 * Si el permiso ya fue concedido, navega directamente a [CameraFragment].
 * Si no fue concedido, lanza el dialogo del sistema para pedirlo.
 * Si el usuario lo niega, muestra un mensaje informativo.
 */
class PermissionsFragment : Fragment() {

    // =====================================================
    // SOLICITUD DE PERMISO
    // =====================================================

    // Lanzador moderno de permisos (reemplaza el deprecated onRequestPermissionsResult)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permiso de camara concedido", Toast.LENGTH_LONG).show()
                navigateToCamera()
            } else {
                Toast.makeText(context, "Permiso de camara denegado", Toast.LENGTH_LONG).show()
            }
        }

    // =====================================================
    // CICLO DE VIDA
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            // Si el permiso ya estaba concedido, ir directo a la camara
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                navigateToCamera()
            }
            // Si no, mostrar el dialogo del sistema para pedirlo
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // =====================================================
    // NAVEGACION
    // =====================================================

    /**
     * Navega al CameraFragment usando el NavController del grafo de navegacion.
     * Se ejecuta dentro de launchWhenStarted para evitar transiciones en estado invalido.
     */
    private fun navigateToCamera() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                PermissionsFragmentDirections.actionPermissionsToCamera()
            )
        }
    }

    companion object {

        /**
         * Verifica si todos los permisos necesarios para la app ya fueron concedidos.
         * Se usa desde CameraFragment en onResume() para detectar si el permiso fue revocado.
         */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
