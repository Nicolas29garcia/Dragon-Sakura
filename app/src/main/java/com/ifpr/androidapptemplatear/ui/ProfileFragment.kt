package com.ifpr.androidapptemplatear.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ifpr.androidapptemplatear.R

class ProfileFragment : Fragment() {
    private lateinit var progress: SakuraProgress
    private lateinit var content: LinearLayout
    private lateinit var shared: SharedModelViewModel
    private var tab = 0

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        progress = SakuraProgress(requireContext())
        shared = ViewModelProvider(requireActivity())[SharedModelViewModel::class.java]
        tab = state?.getInt("tab") ?: 0
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(Color.rgb(26, 15, 23))
        }
        return ScrollView(requireContext()).apply {
            setBackgroundColor(Color.rgb(26, 15, 23))
            addView(content)
            render()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab)
        super.onSaveInstanceState(outState)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 16f, color: Int = Color.WHITE) {
        content.addView(TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(8), 0, dp(8))
        })
    }
    private fun action(value: String, enabled: Boolean = true, run: () -> Unit) {
        content.addView(Button(requireContext()).apply {
            text = value
            isEnabled = enabled
            setOnClickListener { run() }
        })
    }
    private fun notice(value: String) = Toast.makeText(requireContext(), value, Toast.LENGTH_SHORT).show()
    private fun render() {
        content.removeAllViews()
        label("SANTUÁRIO SAKURA", 24f, 0xFFFFA1B8.toInt())
        label("Moedas: ${progress.coins}   •   ${progress.rank}", 18f, 0xFFFFD76B.toInt())
        val nav = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Perfil", "Loja", "Inventário", "Missões").forEachIndexed { index, title ->
            nav.addView(Button(requireContext()).apply {
                text = title
                textSize = 10f
                isAllCaps = false
                minWidth = 0
                setPadding(0, 0, 0, 0)
                isEnabled = tab != index
                setOnClickListener { tab = index; render() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        content.addView(nav)
        when (tab) {
            0 -> profile()
            1 -> shop(false)
            2 -> shop(true)
            3 -> missions()
        }
        action("Voltar aos modelos") { findNavController().navigateUp() }
    }
    private fun profile() {
        label("${progress.avatar}  ${progress.playerName}", 24f)
        label("Patente: ${progress.rank}\nRecorde: ${progress.record}\nDragões registrados: ${progress.captures}/3")
        action("Editar nome e avatar") {
            val panel = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
            val name = EditText(requireContext()).apply { setText(progress.playerName); hint = "Nome" }
            val avatars = listOf("🐉", "🌸", "⚔️", "🔥")
            val picker = Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, avatars)
                setSelection(avatars.indexOf(progress.avatar).coerceAtLeast(0))
            }
            panel.addView(name); panel.addView(picker)
            AlertDialog.Builder(requireContext()).setTitle("Perfil").setView(panel)
                .setPositiveButton("Salvar") { _, _ ->
                    progress.playerName = name.text.toString()
                    progress.avatar = avatars[picker.selectedItemPosition]
                    render()
                }.setNegativeButton("Cancelar", null).show()
        }
        label("Conquistas", 20f, 0xFFFFA1B8.toInt())
        label("${if (progress.captures >= 1) "✓" else "○"} Primeiro dragão\n${if (progress.bossUnlocked) "✓" else "○"} Guardiões reunidos\n${if (progress.captures >= 3) "✓" else "○"} Mestre Sakura\n${if (progress.record >= 40) "✓" else "○"} Explorador (40 pontos)")
        label("Boss secreto • 500 HP", 20f)
        action(if (progress.bossUnlocked) "Enfrentar Ryūjin" else "Capture os dois dragões para desbloquear", progress.bossUnlocked && !shared.jogoEncerrado) {
            shared.selectModel(SakuraRules.BOSS)
            findNavController().navigate(R.id.navigation_camera)
        }
    }
    private fun shop(inventory: Boolean) {
        if (!inventory) {
            action("Resgatar código") {
                val code = EditText(requireContext()).apply { hint = "Digite o código"; isSingleLine = true }
                AlertDialog.Builder(requireContext()).setTitle("Código de recompensa").setView(code)
                    .setPositiveButton("Resgatar") { _, _ -> notice(progress.redeem(code.text.toString())); render() }
                    .setNegativeButton("Cancelar", null).show()
            }
        }
        for (weapon in listOf(false, true)) {
            label(if (weapon) "Katanas" else "Dragões", 20f, 0xFFFFA1B8.toInt())
            SakuraItems.all.filter { it.weapon == weapon && (!inventory || progress.owns(it)) }.forEach { skin ->
                val owned = progress.owns(skin)
                val equipped = progress.equipped(weapon).id == skin.id
                label(skin.name + if (equipped) " • Equipada" else if (owned) " • Desbloqueada" else if (skin.price < 0) " • Código exclusivo" else " • ${skin.price} moedas",
                    16f, if (skin.color == 0) Color.WHITE else skin.color)
                action(if (equipped) "Equipada" else if (owned) "Equipar" else "Desbloquear",
                    !equipped && (owned || (skin.price > 0 && progress.coins >= skin.price))) {
                    if (owned) progress.equip(skin) else if (progress.buy(skin)) progress.equip(skin)
                    render()
                }
            }
        }
        label("Forja Sakura", 20f, 0xFFFFA1B8.toInt())
        label("Nível ${progress.forgeLevel}/5 • Dano: ${progress.damage}")
        action(if (progress.forgeLevel == 5) "Nível máximo" else "Melhorar • ${progress.forgePrice} moedas",
            progress.forgeLevel < 5 && progress.coins >= progress.forgePrice) { progress.upgrade(); render() }
    }
    private fun missions() {
        label("Missões diárias", 22f)
        label("Ataque 5 vezes: ${progress.daily("attacks")}/5")
        label("Capture um dragão: ${progress.daily("captures")}/1")
        label("Visite 2 portais diferentes: ${progress.daily("portals")}/2")
        label("Baú Sakura: 50 moedas e Sakura Sombrio (ou 70 moedas se já possuir).")
        action(if (progress.chestClaimed) "Baú resgatado" else "Abrir Baú Sakura",
            progress.chestReady && !progress.chestClaimed) {
            if (progress.claimChest()) notice("Recompensas desbloqueadas!")
            render()
        }
    }
}
