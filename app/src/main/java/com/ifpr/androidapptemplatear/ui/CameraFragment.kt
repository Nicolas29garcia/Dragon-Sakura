
package com.ifpr.androidapptemplatear.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.ar.core.Config
import com.ifpr.androidapptemplatear.R
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import java.io.IOException
import java.io.File

class CameraFragment : Fragment() {

    private lateinit var sceneView: ArSceneView
    private lateinit var modelNode: ArModelNode
    private lateinit var placeButton: ExtendedFloatingActionButton
    private lateinit var captureButton: ExtendedFloatingActionButton
    private lateinit var sharedViewModel: SharedModelViewModel

    private val cameraPermissionCode = 1001

    private var currentModelFile = "dragao.glb"
    private var modelLoaded = false
    private var modelPlaced = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.fragment_camera,
            container,
            false
        )

        sharedViewModel =
            ViewModelProvider(requireActivity())[SharedModelViewModel::class.java]

        sceneView = view.findViewById<ArSceneView>(R.id.sceneView).apply {
            lightEstimationMode = Config.LightEstimationMode.DISABLED
        }

        placeButton = view.findViewById(R.id.place)
        captureButton = view.findViewById(R.id.capture)

        placeButton.setOnClickListener {
            placeModel()
        }

        captureButton.setOnClickListener {
            captureDragon()
        }

        captureButton.isEnabled = false

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.selectedModel.observe(viewLifecycleOwner) { fileName ->
            if (!fileName.isNullOrBlank()) {
                currentModelFile = fileName

                if (hasCameraPermission()) {
                    loadModel(fileName)
                }
            }
        }

        checkCameraPermission()
    }

    override fun onDestroyView() {
        try {
            if (::modelNode.isInitialized) {
                sceneView.removeChild(modelNode)
            }
        } catch (_: Exception) {
        }

        super.onDestroyView()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkCameraPermission() {
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(android.Manifest.permission.CAMERA),
                cameraPermissionCode
            )
        } else {
            initAR()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (
            requestCode == cameraPermissionCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            initAR()
        } else if (requestCode == cameraPermissionCode) {
            Toast.makeText(
                requireContext(),
                "Permissão de câmera negada.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initAR() {
        loadModel(currentModelFile)
    }

    private fun placeModel() {
        if (!modelLoaded || !::modelNode.isInitialized) {
            Toast.makeText(
                requireContext(),
                "Aguarde o modelo 3D carregar.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        modelNode.anchor()
        modelNode.isEditable = true
        modelNode.isScaleEditable = false

        modelPlaced = true
        captureButton.isEnabled = true

        sceneView.planeRenderer.isVisible = false

        Toast.makeText(
            requireContext(),
            "Modelo posicionado! Você já pode capturar um dragão.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun captureDragon() {
        if (!modelLoaded || !modelPlaced) {
            Toast.makeText(
                requireContext(),
                "Primeiro, carregue e posicione o modelo com PLACE.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val normalizedName = currentModelFile
            .substringAfterLast("/")
            .substringBefore("?")
            .lowercase()

        val isDragon = normalizedName.contains("dragao") ||
                normalizedName.contains("dragon")

        if (!isDragon) {
            Toast.makeText(
                requireContext(),
                "Este objeto não é um dragão. Selecione um modelo de dragão!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val preferences = requireContext().getSharedPreferences(
            "dragon_sakura_collection",
            Context.MODE_PRIVATE
        )

        val capturedModels = preferences
            .getStringSet("captured_models", emptySet<String>())
            .orEmpty()
            .toMutableSet()

        if (!capturedModels.add(currentModelFile)) {
            Toast.makeText(
                requireContext(),
                "Este dragão já está na sua coleção!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        preferences.edit()
            .putStringSet("captured_models", capturedModels)
            .apply()

        Toast.makeText(
            requireContext(),
            "Dragão capturado! Ele foi adicionado ao seu bestiário.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadModel(fileName: String) {
        currentModelFile = fileName
        modelLoaded = false
        modelPlaced = false
        captureButton.isEnabled = false

        try {
            if (::modelNode.isInitialized) {
                sceneView.removeChild(modelNode)
            }
        } catch (_: Exception) {
        }

        modelNode = ArModelNode(
            sceneView.engine,
            PlacementMode.INSTANT
        ).apply {
            isEditable = true
            isScaleEditable = false

            loadModelGlbAsync(
                glbFileLocation = getFilePath(requireContext(), fileName),
                centerOrigin = Position(0f, 0f, 1f)
            ) {
                if (!isAdded) return@loadModelGlbAsync

                modelLoaded = true
                sceneView.planeRenderer.isVisible = true

                Toast.makeText(
                    requireContext(),
                    "Modelo carregado. Toque em PLACE para posicionar.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        sceneView.addChild(modelNode)
    }

    private fun getFilePath(
        context: Context,
        fileName: String
    ): String {
        try {
            context.assets.open(fileName).use { }
            return fileName
        } catch (_: IOException) {
            val internalFile = File(context.filesDir, fileName)

            return if (internalFile.exists()) {
                Uri.fromFile(internalFile).toString()
            } else {
                fileName
            }
        }
    }
}
