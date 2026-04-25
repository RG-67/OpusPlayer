package com.opusplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.opusplayer.R
import com.opusplayer.databinding.FragmentSettingsBinding
import com.opusplayer.service.MusicService
import com.opusplayer.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.username.observe(viewLifecycleOwner) { name ->
            binding.tvUsername.text = name
        }

        viewModel.isDarkMode.observe(viewLifecycleOwner) { dark ->
            binding.switchDarkMode.isChecked = dark
        }

        viewModel.downloadQuality.observe(viewLifecycleOwner) { quality ->
            binding.tvQualityValue.text = quality
        }

        viewModel.sleepTimerMinutes.observe(viewLifecycleOwner) { minutes ->
            binding.tvTimerValue.text = if (minutes == 0) {
                getString(R.string.timer_off)
            } else {
                "$minutes min"
            }
        }

        viewModel.usedBytes.observe(viewLifecycleOwner) { _ ->
            refreshStorageUI()
        }
        viewModel.freeBytes.observe(viewLifecycleOwner) { _ ->
            refreshStorageUI()
        }
    }

    private fun refreshStorageUI() {
        val usedGb = viewModel.getUsedGb()
        val totalGb = viewModel.getTotalGb()
        val freeGb = viewModel.getFreeGb()
        val percent = viewModel.getUsedPercent()

        binding.tvUsedGb.text = "%.1f".format(usedGb)
        binding.tvTotalGb.text = " / %.0f\nGB".format(totalGb)
        binding.pbMemory.progress = percent
        binding.tvUsedLabel.text = "USED: %.2f GB".format(usedGb)
        binding.tvFreeLabel.text = "FREE: %.1f GB".format(freeGb)
    }

    private fun setupClickListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDarkMode(isChecked)
        }

        binding.llQuality.setOnClickListener {
            showQualityDialog()
        }

        binding.llSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        binding.llAbout.setOnClickListener {
            showAboutDialog()
        }

        binding.llHelp.setOnClickListener {
            showHelpDialog()
        }

        binding.tvUsername.setOnClickListener {
            showEditUsernameDialog()
        }
    }

    private fun showQualityDialog() {
        val qualities = arrayOf("Lossless", "High (320 kbps)", "Medium (192 kbps)", "Low (128 kbps)")
        val currentQuality = viewModel.downloadQuality.value ?: "Lossless"
        var selected = qualities.indexOfFirst { it.startsWith(currentQuality.split(" ")[0]) }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.download_quality))
            .setSingleChoiceItems(qualities, selected) { _, which ->
                selected = which
            }
            .setPositiveButton("Save") { _, _ ->
                val quality = qualities[selected].split(" ")[0]
                viewModel.setDownloadQuality(quality)
                Toast.makeText(requireContext(), "Quality set to ${qualities[selected]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes",
            "45 minutes", "1 hour", "2 hours")
        val minutes = arrayOf(0, 5, 10, 15, 30, 45, 60, 120)
        val current = viewModel.sleepTimerMinutes.value ?: 0
        var selected = minutes.indexOfFirst { it == current }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sleep_timer))
            .setSingleChoiceItems(options, selected) { _, which ->
                selected = which
            }
            .setPositiveButton("Set") { _, _ ->
                val mins = minutes[selected]
                viewModel.setSleepTimer(mins)
                // Forward to music service if running
                if (mins == 0) {
                    MusicService.sleepTimerRemaining.postValue(0L)
                }
                // This will be picked up by PlayerActivity via LiveData
                Toast.makeText(
                    requireContext(),
                    if (mins == 0) "Sleep timer off" else "Sleep timer set for ${options[selected]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.about_app))
            .setMessage(
                "${getString(R.string.app_name)}\n" +
                "Version: ${getString(R.string.about_version)}\n\n" +
                getString(R.string.about_description)
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.help_support))
            .setMessage(
                "How to use Opus Player:\n\n" +
                "• HOME: Browse and play your downloaded MP3 files\n" +
                "• SEARCH: Open the built-in browser to find and download MP3s from any website\n" +
                "• Long-press any song for more options\n" +
                "• Tap the mini player to open full player controls\n" +
                "• Set a sleep timer in Settings to auto-stop playback\n\n" +
                "To download a song:\n" +
                "1. Go to Search tab\n" +
                "2. Type a song name or website URL\n" +
                "3. Browse to any MP3 download site\n" +
                "4. Tap the download button/link on the page\n" +
                "5. The song will appear in your Home tab"
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showEditUsernameDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            setText(viewModel.username.value)
            selectAll()
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.setUsername(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStorageInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
