
package com.ifpr.androidapptemplatear.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ifpr.androidapptemplatear.R

class CollectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_collection,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = view.findViewById<LinearLayout>(R.id.collectionList)
        val count = view.findViewById<TextView>(R.id.collectionCount)
        val backButton = view.findViewById<Button>(R.id.backToModels)

        backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        val preferences = requireContext().getSharedPreferences(
            "dragon_sakura_collection",
            Context.MODE_PRIVATE
        )

        val dragons = preferences
            .getStringSet("captured_models", emptySet<String>())
            .orEmpty()
            .toList()
            .sorted()

        count.text = "Dragões capturados: ${dragons.size}"
        list.removeAllViews()

        if (dragons.isEmpty()) {
            val emptyMessage = TextView(requireContext()).apply {
                text = "Sua coleção está vazia.\n\n" +
                        "Explore a realidade aumentada e capture seu primeiro dragão!"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(24, 40, 24, 40)
            }

            list.addView(emptyMessage)
            return
        }

        dragons.forEach { fileName ->
            val dragonName = fileName
                .substringAfterLast("/")
                .substringBeforeLast(".")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }

            val profile = SakuraRules.profile(fileName)
            val place = preferences.getString("capture_place:$fileName", null)
                ?: "Local não registrado"
            val item = TextView(requireContext()).apply {
                text = if (profile != null) {
                    "🐉  ${profile.name}\n\nRaridade: ${profile.rarity}\nElemento: ${profile.element}\n${profile.description}\n\nPrimeira captura: $place"
                } else {
                    "🐉  $dragonName\n\nArquivo: $fileName\nPrimeira captura: $place"
                }
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(16, 20, 16, 20)
                setBackgroundColor(Color.rgb(65, 25, 35))
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }

            list.addView(item, params)
        }
    }
}
