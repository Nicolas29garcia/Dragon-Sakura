package com.ifpr.androidapptemplatear.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.ifpr.androidapptemplatear.R
import com.ifpr.androidapptemplatear.MainActivity
import io.github.sceneview.SceneView
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class CameraFragment : Fragment() {

    private enum class Mode {
        CHECKING,
        AR,
        CAMERA,
        STATIC
    }

    companion object {
        private const val AVAILABILITY_TIMEOUT_MS = 4000L
        private const val AR_TIMEOUT_MS = 15000L
        private const val CAMERA_TIMEOUT_MS = 8000L
    }

    private val localizacaoController = LocalizacaoController(this)

    private var mode = Mode.CHECKING

    private var container: FrameLayout? = null
    private var placeButton: ExtendedFloatingActionButton? = null
    private var captureButton: ExtendedFloatingActionButton? = null
    private var attackButton: ExtendedFloatingActionButton? = null
    private var battleText: TextView? = null
    private var attackJob: Job? = null
    private var swordNode: ModelNode? = null
    private var swordParent: ModelNode? = null
    private var attackSound: MediaPlayer? = null
    private var statusText: TextView? = null
    private var overlay: View? = null

    private var sceneView: SceneView? = null
    private var arView: ARSceneView? = null

    private var modelNode: ModelNode? = null
    private var anchorNode: AnchorNode? = null
    private var placementHit: HitResult? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null

    private var loadJob: Job? = null
    private var availabilityJob: Job? = null
    private var arWatchdogJob: Job? = null
    private var cameraWatchdogJob: Job? = null

    private var generation = 0
    private var modelGeneration = 0
    private var pendingAr = false

    private var arTracking = false
    private var cameraStreaming = false

    private var currentModelFile = "dragao.glb"

    private lateinit var sharedViewModel: SharedModelViewModel

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (container != null && !sharedViewModel.jogoEncerrado) {
                when {
                    !granted -> startPreview(useCamera = false)
                    pendingAr -> startAr()
                    else -> startPreview(useCamera = true)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_camera,
            parent,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        container = view.findViewById(R.id.sceneContainer)
        placeButton = view.findViewById(R.id.place)
        captureButton = view.findViewById(R.id.capture)
        attackButton = view.findViewById(R.id.attack)

        createControls()

        sharedViewModel =
            ViewModelProvider(requireActivity())[
                SharedModelViewModel::class.java
            ]

        val progress = SakuraProgress(requireContext())
        val textoVidas = view.findViewById<TextView>(R.id.textVidas)
        sharedViewModel.vidas.observe(viewLifecycleOwner) { vidas ->
            textoVidas.text = "Vidas: $vidas"
        }
        val textoPontos = view.findViewById<TextView>(R.id.textPontuacao)
        sharedViewModel.pontuacao.observe(viewLifecycleOwner) { pontos ->
            textoPontos.text = "Pontos: $pontos"
            progress.updateRecord(pontos)
        }
        captureButton?.setOnClickListener { captureDragon() }
        attackButton?.setOnClickListener { attackDragon() }

        val selectedModel = sharedViewModel.selectedModel.value

        if (!selectedModel.isNullOrBlank()) {
            currentModelFile = selectedModel
        } else {
            sharedViewModel.selectModel(currentModelFile)
        }

        sharedViewModel.selectedModel.observe(viewLifecycleOwner) { path ->
            if (!sharedViewModel.jogoEncerrado && !path.isNullOrBlank() && path != currentModelFile) {
                currentModelFile = path
                loadModel()
            }
        }

        sharedViewModel.dragonHp.observe(viewLifecycleOwner) { updateBattleControls() }
        sharedViewModel.portal.observe(viewLifecycleOwner) { updateBattleControls() }
        updateBattleControls()

        placeButton?.setOnClickListener {
            if (sharedViewModel.jogoEncerrado) return@setOnClickListener
            when (mode) {
                Mode.AR -> placeModel()

                Mode.CAMERA,
                Mode.STATIC -> {
                    startPreview(useCamera = mode == Mode.CAMERA)
                }

                Mode.CHECKING -> Unit
            }
        }

        checkAvailability()

        val parent = container?.parent as? ViewGroup
        if (parent != null) {
            localizacaoController.conectar(parent) { arquivo, portal ->
                if (!sharedViewModel.jogoEncerrado) {
                    cancelAttack()
                    val before = sharedViewModel.pontuacao.value ?: 0
                    sharedViewModel.iniciarEncontro(arquivo, portal)
                    progress.rewardEncounter((sharedViewModel.pontuacao.value ?: 0) - before)
                    progress.portal(portal)
                    updateBattleControls()
                    Toast.makeText(requireContext(), "$portal: novo encontro!", Toast.LENGTH_SHORT).show()
                }
            }
            placeButton?.bringToFront()
            captureButton?.bringToFront()
        attackButton?.bringToFront()
            parent.findViewById<View>(R.id.titleImage)?.bringToFront()
            parent.findViewById<View>(R.id.textVidas)?.bringToFront()
            parent.findViewById<View>(R.id.textRegra)?.bringToFront()
            textoPontos.bringToFront()
        }
    }

    private fun createControls() {
        val parent = container?.parent as? ViewGroup ?: return

        val layer = FrameLayout(requireContext())

        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.argb(190, 25, 30, 35))
        }

        statusText = TextView(requireContext()).apply {
            text = "Preparando visualização…"
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        panel.addView(statusText)
        battleText = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        panel.addView(battleText)

        layer.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(180)
            }
        )

        parent.addView(
            layer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlay = layer

        parent.findViewById<View>(R.id.titleImage)?.bringToFront()
        placeButton?.bringToFront()
        captureButton?.bringToFront()
        attackButton?.bringToFront()
        parent.findViewById<View>(R.id.textVidas)?.bringToFront()
        parent.findViewById<View>(R.id.textRegra)?.bringToFront()
    }

    /**
     * Identificação por características comuns de emuladores.
     * Por escolha deste aplicativo, eles iniciam sem ARCore.
     */
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains(
                    "Android SDK built for",
                    ignoreCase = true
                ) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    private fun checkAvailability() {
        if (sharedViewModel.jogoEncerrado) return
        availabilityJob?.cancel()

        // Não tenta iniciar ARCore no emulador.
        if (isEmulator()) {
            requestMode(useAr = false)
            return
        }

        val expected = generation
        var resolved = false

        availabilityJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(AVAILABILITY_TIMEOUT_MS)

            if (
                !resolved &&
                container != null &&
                expected == generation
            ) {
                resolved = true
                requestMode(useAr = false)
            }
        }

        try {
            ArCoreApk.getInstance()
                .checkAvailabilityAsync(requireContext()) { availability ->
                    activity?.runOnUiThread {
                        if (
                            !resolved &&
                            container != null &&
                            expected == generation
                        ) {
                            resolved = true
                            availabilityJob?.cancel()
                            availabilityJob = null

                            val installed =
                                availability ==
                                        ArCoreApk.Availability.SUPPORTED_INSTALLED

                            requestMode(useAr = installed)
                        }
                    }
                }
        } catch (_: Exception) {
            if (
                !resolved &&
                container != null &&
                expected == generation
            ) {
                resolved = true
                availabilityJob?.cancel()
                availabilityJob = null

                requestMode(useAr = false)
            }
        }
    }

    private fun requestMode(useAr: Boolean) {
        if (sharedViewModel.jogoEncerrado) return
        pendingAr = useAr

        val permitted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        when {
            !permitted -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }

            useAr -> startAr()

            else -> startPreview(useCamera = true)
        }
    }

    private fun startAr() {
        if (sharedViewModel.jogoEncerrado) return
        val host = container ?: return

        clearScene()
        mode = Mode.AR

        val expected = generation

        statusText?.text = "Aponte para uma mesa ou para o chão."

        placeButton?.apply {
            text = "Posicionar"
            isEnabled = false
        }

        try {
            val ar = ARSceneView(requireContext())

            arView = ar
            sceneView = ar

            ar.configureSession { _, config ->
                config.planeFindingMode =
                    Config.PlaneFindingMode.HORIZONTAL

                config.lightEstimationMode =
                    Config.LightEstimationMode.DISABLED
            }

            ar.onSessionFailed = {
                host.post {
                    if (
                        container != null &&
                        expected == generation &&
                        mode == Mode.AR
                    ) {
                        startPreview(useCamera = true)
                    }
                }
            }

            ar.onSessionUpdated = { _, frame ->
                if (
                    expected == generation &&
                    mode == Mode.AR
                ) {
                    arTracking =
                        frame.camera.trackingState ==
                                TrackingState.TRACKING

                    if (anchorNode == null) {
                        placementHit =
                            if (
                                arTracking &&
                                ar.width > 0 &&
                                ar.height > 0
                            ) {
                                frame.hitTest(
                                    ar.width / 2f,
                                    ar.height / 2f
                                ).firstOrNull { hit ->
                                    val plane = hit.trackable as? Plane

                                    plane != null &&
                                            plane.type ==
                                            Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                            plane.isPoseInPolygon(hit.hitPose)
                                }
                            } else {
                                null
                            }

                        val ready =
                            placementHit != null &&
                                    modelNode != null

                        placeButton?.isEnabled = ready

                        statusText?.text =
                            if (ready) {
                                "Superfície encontrada. Toque em Posicionar."
                            } else {
                                "Mova o aparelho devagar sobre uma superfície."
                            }
                    }
                }
            }

            ar.lifecycle = viewLifecycleOwner.lifecycle

            host.addView(ar, fullSize())

            startArWatchdog(expected)
            loadModel()

        } catch (_: Exception) {
            startPreview(useCamera = true)
        }
    }

    /**
     * Troca automaticamente quando o ARCore não consegue rastrear.
     * Conta somente o tempo em que a tela está em primeiro plano.
     * A ausência de uma superfície, sozinha, não provoca a troca.
     */
    private fun startArWatchdog(expected: Int) {
        arWatchdogJob?.cancel()

        arWatchdogJob = viewLifecycleOwner.lifecycleScope.launch {
            var withoutTrackingMs = 0L
            var lastTime = SystemClock.elapsedRealtime()

            while (isActive) {
                delay(500L)

                if (
                    container == null ||
                    expected != generation ||
                    mode != Mode.AR
                ) {
                    return@launch
                }

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTime
                lastTime = now

                val resumed = viewLifecycleOwner.lifecycle
                    .currentState.isAtLeast(Lifecycle.State.RESUMED)

                if (!resumed || arTracking) {
                    withoutTrackingMs = 0L
                } else {
                    withoutTrackingMs += elapsed
                }

                if (withoutTrackingMs >= AR_TIMEOUT_MS) {
                    startPreview(useCamera = true)
                    return@launch
                }
            }
        }
    }

    private fun startPreview(useCamera: Boolean) {
        if (sharedViewModel.jogoEncerrado) return
        val host = container ?: return

        clearScene()

        mode = if (useCamera) Mode.CAMERA else Mode.STATIC

        placeButton?.apply {
            text = "Centralizar"
            isEnabled = false
        }

        statusText?.text = if (useCamera) {
            "Modelo sobre a câmera, sem fixação no ambiente."
        } else {
            "Visualização 3D sem câmera."
        }

        host.setBackgroundColor(Color.rgb(45, 55, 65))

        val cameraView = if (useCamera) {
            PreviewView(requireContext()).apply {
                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE

                scaleType = PreviewView.ScaleType.FILL_CENTER

                host.addView(this, fullSize())
            }
        } else {
            null
        }

        try {
            val scene = SceneView(
                context = requireContext(),
                isOpaque = !useCamera
            )

            scene.lifecycle = viewLifecycleOwner.lifecycle
            scene.cameraNode.position = Position(0f, 0f, 3f)
            scene.cameraNode.lookAt(Position(0f, 0f, 0f))

            sceneView = scene

            host.addView(scene, fullSize())

            loadModel()

            if (cameraView != null) {
                openCamera(cameraView)
            }
        } catch (_: Exception) {
            statusText?.text = "Não foi possível iniciar a cena 3D."
        }
    }

    private fun openCamera(previewView: PreviewView) {
        val context = context ?: return

        val expected = generation
        val owner = viewLifecycleOwner

        // O prazo também cobre a obtenção do CameraProvider.
        startCameraWatchdog(expected)

        try {
            val future = ProcessCameraProvider.getInstance(context)

            future.addListener({
                if (
                    container == null ||
                    expected != generation ||
                    mode != Mode.CAMERA
                ) {
                    return@addListener
                }

                try {
                    val provider = future.get()

                    val selector = when {
                        provider.hasCamera(
                            CameraSelector.DEFAULT_BACK_CAMERA
                        ) -> CameraSelector.DEFAULT_BACK_CAMERA

                        provider.hasCamera(
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        ) -> CameraSelector.DEFAULT_FRONT_CAMERA

                        else -> {
                            startPreview(useCamera = false)
                            return@addListener
                        }
                    }

                    val preview = Preview.Builder().build()

                    preview.setSurfaceProvider(
                        previewView.surfaceProvider
                    )

                    cameraProvider = provider
                    cameraPreview = preview

                    previewView.previewStreamState.observe(owner) { state ->
                        if (
                            expected == generation &&
                            mode == Mode.CAMERA
                        ) {
                            cameraStreaming =
                                state == PreviewView.StreamState.STREAMING
                        }
                    }

                    provider.bindToLifecycle(
                        owner,
                        selector,
                        preview
                    )

                } catch (_: Exception) {
                    if (
                        expected == generation &&
                        container != null
                    ) {
                        startPreview(useCamera = false)
                    }
                }
            }, ContextCompat.getMainExecutor(context))

        } catch (_: Exception) {
            if (
                expected == generation &&
                container != null
            ) {
                startPreview(useCamera = false)
            }
        }
    }

    private fun startCameraWatchdog(expected: Int) {
        cameraWatchdogJob?.cancel()

        cameraWatchdogJob = viewLifecycleOwner.lifecycleScope.launch {
            var withoutCameraMs = 0L
            var lastTime = SystemClock.elapsedRealtime()

            while (isActive) {
                delay(500L)

                if (
                    container == null ||
                    expected != generation ||
                    mode != Mode.CAMERA
                ) {
                    return@launch
                }

                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTime
                lastTime = now

                val resumed = viewLifecycleOwner.lifecycle
                    .currentState.isAtLeast(Lifecycle.State.RESUMED)

                if (!resumed || cameraStreaming) {
                    withoutCameraMs = 0L
                } else {
                    withoutCameraMs += elapsed
                }

                if (withoutCameraMs >= CAMERA_TIMEOUT_MS) {
                    startPreview(useCamera = false)
                    return@launch
                }
            }
        }
    }

    private fun loadModel() {
        if (sharedViewModel.jogoEncerrado) return
        val scene = sceneView ?: return
        val location = modelLocation(SakuraRules.assetFile(currentModelFile))

        if (location == null) {
            statusText?.text =
                "Modelo não encontrado: $currentModelFile"

            placeButton?.isEnabled = false
            return
        }

        loadJob?.cancel()
        removeModel()

        val expected = generation
        val expectedModel = ++modelGeneration
        val isAr = mode == Mode.AR
        val fileName = currentModelFile
        val appContext = requireContext().applicationContext
        val skinColor = if (fileName == SakuraRules.BOSS) 0xFF713D9E.toInt() else if (SakuraRules.profile(fileName) != null) SakuraProgress(appContext).equipped(false).color else 0

        placeButton?.isEnabled = false

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val renderPath = if (skinColor == 0) location else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    SakuraSkinFiles.modelPath(appContext, fileName, skinColor)
                }
                if (expected != generation || expectedModel != modelGeneration || sceneView !== scene) return@launch
                val instance = scene.modelLoader.loadModelInstance(renderPath)

                if (
                    expected != generation ||
                    expectedModel != modelGeneration ||
                    sceneView !== scene
                ) {
                    return@launch
                }

                if (instance == null) {
                    statusText?.text = "Não foi possível abrir o modelo."
                    return@launch
                }

                val node = ModelNode(
                    modelInstance = instance,
                    scaleToUnits = if (isAr) 0.5f else 1.5f,
                    centerOrigin = Position(0f, 0f, 0f)
                )

                node.isEditable = true
                modelNode = node

                if (!isAr) {
                    scene.addChildNode(node)
                    placeButton?.isEnabled = true
                    updateBattleControls()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (
                    expected == generation &&
                    expectedModel == modelGeneration
                ) {
                    statusText?.text =
                        "Erro ao carregar: $fileName"
                }
            }
        }
    }

    private fun placeModel() {
        if (sharedViewModel.jogoEncerrado) return
        val ar = arView ?: return

        if (anchorNode != null) {
            loadModel()
            return
        }

        val hit = placementHit ?: return
        val model = modelNode ?: return

        try {
            val anchor = AnchorNode(
                engine = ar.engine,
                anchor = hit.createAnchor()
            )

            anchor.addChildNode(model)
            ar.addChildNode(anchor)

            anchorNode = anchor
            placementHit = null
            updateBattleControls()

            placeButton?.apply {
                text = "Reposicionar"
                isEnabled = true
            }

            statusText?.text = "Objeto posicionado no ambiente."

        } catch (_: Exception) {
            statusText?.text =
                "Não foi possível posicionar. Tente outra superfície."
        }
    }

    private fun updateBattleControls() {
        if (!::sharedViewModel.isInitialized) return
        val profile = SakuraRules.profile(currentModelFile)
        val hp = sharedViewModel.dragonHp.value ?: 0
        val captured = sharedViewModel.foiCapturado(currentModelFile)
        val ready = !sharedViewModel.jogoEncerrado && modelNode != null &&
            (mode != Mode.AR || anchorNode != null) && attackJob?.isActive != true
        attackButton?.isEnabled = ready && profile != null && hp > 0 && !captured
        captureButton?.isEnabled = ready && (profile == null || (hp == 0 && !captured))
        val portal = sharedViewModel.portal.value ?: "Exploração livre"
        val weapon = context?.let { SakuraProgress(it) }
        battleText?.text = if (profile == null) {
            "$portal\nObjeto não capturável: tentar capturar custa uma vida."
        } else {
            val status = if (captured) "Capturado" else "HP: $hp/${profile.maxHp}"
            "$portal\n${profile.name} • ${profile.rarity} • $status\n${weapon?.equipped(true)?.name ?: "Katana"} • Dano: ${weapon?.damage ?: 25}"
        }
    }

    private fun attackDragon() {
        if (sharedViewModel.jogoEncerrado || attackJob?.isActive == true) return
        val dragon = modelNode ?: return
        val scene = sceneView ?: return
        if (mode == Mode.AR && anchorNode == null) return
        val file = currentModelFile
        val appContext = requireContext().applicationContext
        val progress = SakuraProgress(appContext)
        val weaponColor = progress.equipped(true).color
        if (SakuraRules.profile(file) == null || sharedViewModel.foiCapturado(file) ||
            (sharedViewModel.dragonHp.value ?: 0) <= 0) return

        attackButton?.isEnabled = false
        captureButton?.isEnabled = false
        attackJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Usa o modelo de katana já presente no projeto.
                val weaponPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    SakuraSkinFiles.modelPath(appContext, "katana.glb", weaponColor)
                }
                if (modelNode !== dragon || sceneView !== scene || sharedViewModel.jogoEncerrado) return@launch
                val instance = scene.modelLoader.loadModelInstance(weaponPath)
                if (modelNode !== dragon || sceneView !== scene || sharedViewModel.jogoEncerrado) return@launch
                if (instance != null) {
                    val sword = ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 0.55f,
                        centerOrigin = Position(0f, 0f, 0f)
                    )
                    sword.isEditable = false
                    swordNode = sword
                    swordParent = dragon
                    sword.position = Position(0.7f, 0.45f, 0.15f)
                    dragon.addChildNode(sword)
                    playAttackSound()
                    repeat(10) { step ->
                        val progress = (step + 1) / 10f
                        sword.position = Position(0.7f - 1.4f * progress, 0.45f - 0.4f * progress, 0.15f)
                        delay(25L)
                    }
                } else {
                    playAttackSound()
                    delay(250L)
                }
                if (modelNode === dragon && currentModelFile == file) {
                    if (sharedViewModel.atacarDragao(file, progress.damage)) progress.event("attacks")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // O combate continua mesmo se o efeito 3D falhar.
                if (modelNode === dragon && currentModelFile == file && !sharedViewModel.jogoEncerrado) {
                    playAttackSound()
                    if (sharedViewModel.atacarDragao(file, progress.damage)) progress.event("attacks")
                }
            } finally {
                removeSword()
                attackJob = null
                updateBattleControls()
            }
        }
    }

    private fun playAttackSound() {
        runCatching {
            attackSound?.release()
            attackSound = MediaPlayer.create(requireContext(), R.raw.katana)?.apply {
                setOnCompletionListener { player ->
                    if (attackSound === player) attackSound = null
                    player.release()
                }
                start()
            }
        }
    }

    private fun removeSword() {
        val sword = swordNode
        if (sword != null) {
            runCatching { swordParent?.removeChildNode(sword) }
            runCatching { sword.destroy() }
        }
        swordNode = null
        swordParent = null
    }

    private fun cancelAttack() {
        attackJob?.cancel()
        attackJob = null
        removeSword()
        runCatching { attackSound?.release() }
        attackSound = null
    }

    private fun captureDragon() {
        if (sharedViewModel.jogoEncerrado || modelNode == null) return
        if (mode == Mode.AR && anchorNode == null) return

        if (attackJob?.isActive == true) return
        val profile = SakuraRules.profile(currentModelFile)

        if (profile == null) {
            perderVida()
            return
        }

        val preferences = requireContext().getSharedPreferences(
            "dragon_sakura_collection", Context.MODE_PRIVATE
        )
        val capturedModels = preferences
            .getStringSet("captured_models", emptySet<String>())
            .orEmpty().toMutableSet()

        if (!sharedViewModel.registrarCaptura(currentModelFile)) return

        SakuraProgress(requireContext()).event("captures")
        val firstCapture = capturedModels.add(currentModelFile)
        if (firstCapture) {
            preferences.edit()
                .putStringSet("captured_models", capturedModels)
                .putString("capture_place:$currentModelFile", sharedViewModel.portal.value ?: "Exploração livre")
                .apply()
        }
        updateBattleControls()
        val message = if (firstCapture) "Dragão capturado! Adicionado ao bestiário."
            else "Dragão capturado nesta partida! Já consta no bestiário."
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun perderVida() {
        if (!sharedViewModel.perderVida(SystemClock.elapsedRealtime())) return
        if (sharedViewModel.vidas.value == 0) {
            encerrarJogo("Suas vidas acabaram.")
        } else {
            Toast.makeText(requireContext(), "Objeto incorreto! Você perdeu uma vida.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encerrarJogo(motivo: String) {
        if (!sharedViewModel.encerrarJogo()) return

        placeButton?.isEnabled = false
        captureButton?.isEnabled = false
        availabilityJob?.cancel()
        availabilityJob = null
        modelNode?.isEditable = false
        cancelAttack()
        attackButton?.isEnabled = false
        localizacaoController.encerrarPartida()
        (activity as? MainActivity)?.pararSomDaPartida()

        // Invalida callbacks pendentes e cancela o carregamento antes de finalizar.
        generation++
        modelGeneration++
        loadJob?.cancel()
        arWatchdogJob?.cancel()
        cameraWatchdogJob?.cancel()

        val tela = Intent(requireContext(), GameOverActivity::class.java)
        tela.putExtra("motivo", motivo)
        startActivity(tela)
        requireActivity().finish()
    }

    private fun modelLocation(fileName: String): String? {
        val context = context ?: return null

        try {
            context.assets.open(fileName).use { }
            return fileName
        } catch (_: Exception) {
            // Procura no armazenamento interno.
        }

        val file = File(context.filesDir, fileName)

        return if (file.isFile) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }

    private fun removeModel() {
        cancelAttack()
        attackButton?.isEnabled = false
        captureButton?.isEnabled = false
        val scene = sceneView

        modelNode?.let { model ->
            runCatching {
                anchorNode?.removeChildNode(model)
                scene?.removeChildNode(model)
                model.destroy()
            }
        }

        modelNode = null

        anchorNode?.let { anchor ->
            runCatching {
                scene?.removeChildNode(anchor)
                anchor.anchor.detach()
                anchor.destroy()
            }
        }

        anchorNode = null
        placementHit = null

        if (mode == Mode.AR) {
            placeButton?.text = "Posicionar"
        }
    }

    private fun clearScene() {
        generation++
        modelGeneration++

        arWatchdogJob?.cancel()
        arWatchdogJob = null

        cameraWatchdogJob?.cancel()
        cameraWatchdogJob = null

        loadJob?.cancel()
        loadJob = null

        cameraPreview?.let { preview ->
            runCatching {
                cameraProvider?.unbind(preview)
            }
        }

        cameraPreview = null
        cameraProvider = null

        arTracking = false
        cameraStreaming = false

        removeModel()

        sceneView?.let { scene ->
            runCatching {
                scene.lifecycle = null
                scene.destroy()
            }
        }

        sceneView = null
        arView = null

        container?.removeAllViews()
    }

    private fun fullSize() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        availabilityJob?.cancel()
        availabilityJob = null

        clearScene()

        overlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        overlay = null
        statusText = null
        placeButton = null
        captureButton = null
        attackButton = null
        battleText = null
        container = null

        pendingAr = false
        mode = Mode.CHECKING

        super.onDestroyView()
    }
}