package hideo.miyauchi.android.FlightSimulationWithARCore

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.*
import com.google.ar.core.common.helpers.DisplayRotationHelper
import com.google.ar.core.common.helpers.TapHelper
import com.google.ar.core.common.helpers.TrackingStateHelper
import com.google.ar.core.common.rendering.AugmentedImageRenderer
import com.google.ar.core.common.rendering.BackgroundRenderer
import com.google.ar.core.common.rendering.ObjectRenderer
import com.google.ar.core.common.rendering.PlaneRenderer
import hideo.miyauchi.android.databinding.FragmentFlightSimulationWithArcoreBinding
import kotlinx.coroutines.*
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.Companion.convertQuaternionToEuler
import hideo.miyauchi.android.FlightSimulationWithARCore.Utility.Companion.convertEulerToRotateMatrix

class FlightSimulationWithARCoreFragment : Fragment(), GLSurfaceView.Renderer {

    private lateinit var _activity: FragmentActivity
    private lateinit var _context: Context
    private lateinit var binding: FragmentFlightSimulationWithArcoreBinding

    private lateinit var trackingStateHelper: TrackingStateHelper
    private lateinit var tapHelper: TapHelper
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var backgroundRenderer: BackgroundRenderer
    private lateinit var planeRenderer: PlaneRenderer // Plane
    private lateinit var markerImageRenderer: AugmentedImageRenderer // Marker
    private lateinit var aircraftImageRenderer: ObjectRenderer // Aircraft
    private lateinit var session: Session
    private lateinit var mediaPlayer: MediaPlayer // Sound

    private val markerImageMap: MutableMap<Int, Pair<AugmentedImage, Anchor>> = mutableMapOf()
    private val aircrafts: ArrayList<Anchor> = ArrayList()

    private val coordinateScale = 1f / 1500f
    private val flightDynamics: FlightDynamics = FlightDynamics()

    private enum class RunMode { DISABLE, RESET, STOP, START }

    private var runMode: RunMode = RunMode.DISABLE

    private var aircraftMove = Triple(0f, 0f, 0f) // x, y, z
    private var aircraftRot = Triple(0f, 0f, 0f) // x, y, z
    private var markerRot = Triple(0f, 0f, 0f) // x, y, z

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val translateMatrix = FloatArray(16)
    private val translatedModelMatrix = FloatArray(16)
    private val rotateTranslatedModelMatrix = FloatArray(16)
    private val colorCorrectionRgba = FloatArray(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _activity = requireActivity()
        _context = requireContext()
    }

    @SuppressLint("ClickableViewAccessibility") override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFlightSimulationWithArcoreBinding.inflate(inflater, container, false)

        // Generate instances
        trackingStateHelper = TrackingStateHelper(_activity)
        tapHelper = TapHelper(_activity)
        displayRotationHelper = DisplayRotationHelper(_activity)
        backgroundRenderer = BackgroundRenderer()
        planeRenderer = PlaneRenderer()
        markerImageRenderer = AugmentedImageRenderer()
        aircraftImageRenderer = ObjectRenderer()
        session = Session(_context)
        mediaPlayer = MediaPlayer.create(_context, hideo.miyauchi.android.R.raw.jetsound)

        // Set up renderer.
        binding.Surfaceview.preserveEGLContextOnPause = true
        binding.Surfaceview.setEGLContextClientVersion(2)
        binding.Surfaceview.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.Surfaceview.setRenderer(this)
        binding.Surfaceview.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.Surfaceview.setWillNotDraw(false)

        // Read database
        var augmentedImageDatabase: AugmentedImageDatabase? = null
        try {
            val ins = _context.assets.open("FlightSimulationWithARCore/image_reference_database.imgdb")
            augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, ins)
        } catch (e: IOException) {
            Toast.makeText(_context, "Could not setup augmented image database", Toast.LENGTH_LONG).show()
        }

        // Configure session
        val config = Config(session)
        config.focusMode = Config.FocusMode.FIXED
        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        } else {
            config.depthMode = Config.DepthMode.DISABLED
        }
        if (augmentedImageDatabase != null) {
            config.augmentedImageDatabase = augmentedImageDatabase
        }
        session.configure(config)

        // Sound setting
        mediaPlayer.setLooping(true)

        // Tap gesture listener
        binding.Surfaceview.setOnTouchListener(tapHelper)

        // initialize mode
        setRunMode(RunMode.DISABLE)

        // RESET button listener
        binding.resetButton.setOnClickListener {
            if (runMode != RunMode.DISABLE) {
                setRunMode(RunMode.RESET)
            }
        }

        // START button listener
        binding.startButton.setOnCheckedChangeListener { _, isChecked ->
            if (runMode != RunMode.DISABLE) {
                if (isChecked) {
                    setRunMode(RunMode.START)
                } else {
                    setRunMode(RunMode.STOP)
                }
            }
        }

        // DEMO check button listener
        binding.demoButton.setOnCheckedChangeListener { _, _ ->
            if (runMode != RunMode.DISABLE) {
                setRunMode(RunMode.RESET)
            }
        }

        return binding.root
    }

    private fun setRunMode(mode: RunMode) {
        _activity.runOnUiThread {
            when (mode) {
                RunMode.DISABLE -> {
                    binding.resetButton.isEnabled = false
                    binding.startButton.isEnabled = false
                    binding.startButton.isChecked = false
                }
                RunMode.RESET -> {
                    binding.resetButton.isEnabled = true
                    binding.startButton.isEnabled = true
                    binding.startButton.isChecked = false
                    resetFlightScenario()
                }
                RunMode.STOP -> {
                    mediaPlayer.stop()
                    mediaPlayer.prepare()
                }
                RunMode.START -> {
                    mediaPlayer.start()
                }
            }
            runMode = mode
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                when (runMode) {
                    RunMode.DISABLE -> {
                        delay(500)
                    }
                    RunMode.RESET -> {
                        val flightDynamicsResult = flightDynamics.reset()
                        aircraftMove = Triple(flightDynamicsResult[0] * coordinateScale, flightDynamicsResult[2] * coordinateScale, flightDynamicsResult[1] * coordinateScale)
                        aircraftRot = Triple(-1 * markerRot.third, 0f, markerRot.first)
                        delay(200)
                    }
                    RunMode.STOP -> {
                        delay(500)
                    }
                    RunMode.START -> {
                        val (pitch, roll) = if (binding.demoButton.isChecked) {
                            getGlightScenario()
                        } else {
                            Pair(markerRot.first, -1 * markerRot.third)
                        }
                        val elevator = flightDynamics.keepPitchAngle(pitch)
                        val aileron = flightDynamics.keepRollAngle(roll)
                        val throttle = flightDynamics.keepVelocity()
                        val flightDynamicsResult = flightDynamics.solve(elevator, aileron, throttle)

                        aircraftMove = Triple(flightDynamicsResult[0] * coordinateScale, flightDynamicsResult[2] * coordinateScale, flightDynamicsResult[1] * coordinateScale)
                        aircraftRot = Triple(flightDynamicsResult[3], -1 * flightDynamicsResult[5], flightDynamicsResult[4])
                        delay(2 * (binding.speedSeekbar.max.toLong() - binding.speedSeekbar.progress.toLong() + 1))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        session.close()
        mediaPlayer.stop()
        mediaPlayer.prepare()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        session.resume()
        binding.Surfaceview.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        displayRotationHelper.onPause()
        binding.Surfaceview.onPause()
        session.pause()
        mediaPlayer.stop()
        mediaPlayer.prepare()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        try {
            backgroundRenderer.createOnGlThread(_context)
            planeRenderer.createOnGlThread(_context, "FlightSimulationWithARCore/models/trigrid.png")
            markerImageRenderer.createOnGlThread(_context)
            aircraftImageRenderer.createOnGlThread(_context, "FlightSimulationWithARCore/models/sukhoi_upsidedown.obj", "FlightSimulationWithARCore/models/sukhoi_upsidedown.jpg")
        } catch (e: IOException) {
            Toast.makeText(_context, "Failed to read an asset file", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        displayRotationHelper.updateSessionIfNeeded(session)

        try {
            session.setCameraTextureName(backgroundRenderer.textureId)
            val frame: Frame = session.update()
            val camera: Camera = frame.camera
            trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)
            backgroundRenderer.draw(frame)
            handleTap(frame, camera)
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
            camera.getViewMatrix(viewMatrix, 0)
            if (aircrafts.isEmpty()) {
                planeRenderer.drawPlanes(session.getAllTrackables(Plane::class.java), camera.displayOrientedPose, projMatrix)
            }
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)
            drawMarkerImage(frame, projMatrix, viewMatrix, colorCorrectionRgba)
            drawAircraftImage(projMatrix, viewMatrix, colorCorrectionRgba)
        } catch (t: Throwable) {
            Toast.makeText(_context, "Exception on the OpenGL thread", Toast.LENGTH_LONG).show()
        }
    }

    private fun drawMarkerImage(frame: Frame, projmtx: FloatArray, viewmtx: FloatArray, colorCorrectionRgba: FloatArray) {
        val updatedMarkerImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        for (marker in updatedMarkerImages) {
            when (marker.trackingState) {
                TrackingState.PAUSED -> { // do nothing
                }
                TrackingState.TRACKING -> {
                    if (!markerImageMap.containsKey(marker.index)) {
                        val centerPoseAnchor = marker.createAnchor(marker.centerPose)
                        markerImageMap[marker.index] = Pair(marker, centerPoseAnchor)
                    }
                }
                TrackingState.STOPPED -> {
                    markerImageMap.remove(marker.index)
                }
            }
        }

        markerImageMap.forEach {
            val augmentedImage: AugmentedImage = markerImageMap[it.key]!!.first
            val centerAnchor: Anchor = markerImageMap[it.key]!!.second
            if (augmentedImage.trackingState == TrackingState.TRACKING) { // calculate marker angle for aircraft angle
                val pose: Pose = centerAnchor.pose
                val (rot_x, rot_y, rot_z) = convertQuaternionToEuler(pose.qw(), pose.qx(), pose.qy(), pose.qz())
                val markerPitchAngleOffset = 30f
                markerRot = Triple(rot_x - markerPitchAngleOffset, rot_y, rot_z)

                markerImageRenderer.draw(viewmtx, projmtx, augmentedImage, centerAnchor, colorCorrectionRgba)
            }
        }
    }

    private fun drawAircraftImage(projmtx: FloatArray, viewmtx: FloatArray, colorCorrectionRgba: FloatArray) {
        for (aircraft in aircrafts) {
            when (aircraft.trackingState) {
                TrackingState.PAUSED -> { // do nothing
                }
                TrackingState.TRACKING -> {
                    val aircraftColor = floatArrayOf(255.0f, 255.0f, 255.0f, 1.0f)

                    Matrix.setIdentityM(translateMatrix, 0)
                    Matrix.translateM(translateMatrix, 0, aircraftMove.first, aircraftMove.second, aircraftMove.third)

                    aircraft.pose.toMatrix(modelMatrix, 0)
                    Matrix.multiplyMM(translatedModelMatrix, 0, modelMatrix, 0, translateMatrix, 0)

                    val rotateMatrix = convertEulerToRotateMatrix(aircraftRot.first, aircraftRot.second, aircraftRot.third)
                    Matrix.multiplyMM(rotateTranslatedModelMatrix, 0, translatedModelMatrix, 0, rotateMatrix, 0)

                    aircraftImageRenderer.updateModelMatrix(rotateTranslatedModelMatrix, 0.02f)
                    aircraftImageRenderer.draw(viewmtx, projmtx, colorCorrectionRgba, aircraftColor)
                }
                TrackingState.STOPPED -> { // do nothing
                }
            }
        }
    }

    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = tapHelper.poll()
        if (tap != null) {
            if (aircrafts.isEmpty()) {
                if (camera.trackingState === TrackingState.TRACKING) {
                    val hitResultList: List<HitResult> = frame.hitTest(tap)
                    for (hit in hitResultList) {
                        val trackable: Trackable = hit.trackable
                        if ((trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)) {
                            aircrafts.add(hit.createAnchor())
                            setRunMode(RunMode.RESET)
                            break
                        }
                    }
                }
            } else {
                aircrafts.forEach {
                    it.detach()
                }
                aircrafts.removeAll { true }
                setRunMode(RunMode.DISABLE)
            }
        }
    }

    private var scenarioTime: Int = 0

    private fun resetFlightScenario() {
        scenarioTime = 0
    }

    private fun getGlightScenario(): Pair<Float, Float> {
        val (pitch, roll) = when {
            scenarioTime < 30 -> Pair(0f, 0f)
            scenarioTime < 530 -> Pair(0f, 60f)
            scenarioTime < 810 -> Pair(12f, 0f)
            scenarioTime < 1480 -> Pair(0f, -45f)
            scenarioTime < 1720 -> Pair(-10f, 0f)
            else -> {
                scenarioTime = 0
                Pair(0f, 0f)
            }
        }
        scenarioTime += 1
        return Pair(pitch, roll)
    }

    companion object {
        private val LOG_TAG = this::class.java.simpleName
    }
}