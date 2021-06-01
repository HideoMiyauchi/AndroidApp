package hideo.miyauchi.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import hideo.miyauchi.android.databinding.ActivityMainBinding
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.*
import com.google.ar.core.common.helpers.CameraPermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var isSupportedArCoreDevice: Boolean = false
    private var userRequestedInstall: Boolean = true
    private var installedArCore: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_contactme, R.id.nav_flightsimulationwitharcore), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        drawerLayout.openDrawer(Gravity.LEFT)

        binding.navView.menu.findItem(R.id.nav_flightsimulationwitharcore).setEnabled(false)
        
        isSupportedArCoreDevice = isArCoreSupportDevice()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()

        // Check camera permission.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        if (! installAndUpdateGooglePlayService()) {
            return
        }

        binding.navView.menu.findItem(R.id.nav_flightsimulationwitharcore).setEnabled(installedArCore)
    }

    private fun installAndUpdateGooglePlayService(): Boolean {
        var message: String? = null

        if (isSupportedArCoreDevice == true) {
            if (installedArCore == false) {
                try {
                    when (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                        ArCoreApk.InstallStatus.INSTALLED -> {
                            installedArCore = true
                        }
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            if (userRequestedInstall == true) {
                                userRequestedInstall = false
                                return false
                            }
                            message = "Please update ARCore"
                        }
                    }
                } catch (e: UnavailableArcoreNotInstalledException) {
                    message = "Please install ARCore"
                } catch (e: UnavailableUserDeclinedInstallationException) {
                    message = "Please install ARCore"
                } catch (e: UnavailableApkTooOldException) {
                    message = "Please update ARCore"
                } catch (e: UnavailableSdkTooOldException) {
                    message = "Please update this app"
                } catch (e: Exception) {
                    message = "This device does not support AR"
                }
            }
        } else {
            message = "This device does not support AR"
        }
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // Determines if ARCore is supported on this device.
    private fun isArCoreSupportDevice(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            var ret = false
            Handler(Looper.getMainLooper()).postDelayed({
                ret = isArCoreSupportDevice()
            }, 200)
            return ret
        }
        return availability.isSupported
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }
}
