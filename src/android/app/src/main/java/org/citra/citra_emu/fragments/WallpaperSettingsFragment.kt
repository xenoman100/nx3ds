// Copyright 2026 NX3DS Project
// Licensed under GPLv3 or any later version.

package org.citra.citra_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.transition.MaterialSharedAxis
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.FragmentWallpaperSettingsBinding
import org.citra.citra_emu.viewmodel.HomeViewModel

class WallpaperSettingsFragment : Fragment() {
    private var _binding: FragmentWallpaperSettingsBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()

    private val preferences
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    companion object {
        private const val PREF_WALLPAPER_STYLE = "nx3ds_wallpaper_style"
        private const val PREF_WALLPAPER_SPEED = "nx3ds_wallpaper_speed"
        private const val PREF_WALLPAPER_QUALITY = "nx3ds_wallpaper_quality"
        private const val PREF_WALLPAPER_FPS = "nx3ds_wallpaper_fps"
        private const val PREF_WALLPAPER_MOTION = "nx3ds_wallpaper_motion"
        private const val PREF_WALLPAPER_TOUCH = "nx3ds_wallpaper_touch"
        private const val PREF_WALLPAPER_PARALLAX = "nx3ds_wallpaper_parallax"
        private const val PREF_WALLPAPER_STATIC = "nx3ds_wallpaper_static"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWallpaperSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        homeViewModel.setNavigationVisibility(visible = false, animated = true)
        homeViewModel.setStatusBarShadeVisibility(visible = false)

        binding.toolbarWallpaper.setNavigationOnClickListener {
            binding.root.findNavController().popBackStack()
        }

        setupDropdowns()
        setupSwitches()
        setInsets()
    }

    private fun setupDropdowns() {
        val styles = resources.getStringArray(R.array.nx3ds_wallpaper_styles)
        val speeds = resources.getStringArray(R.array.nx3ds_wallpaper_speeds)
        val qualities = resources.getStringArray(R.array.nx3ds_wallpaper_qualities)
        val frameRates = resources.getStringArray(R.array.nx3ds_wallpaper_frame_rates)

        binding.dropdownWallpaperStyle.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, styles)
        )
        binding.dropdownWallpaperSpeed.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, speeds)
        )
        binding.dropdownWallpaperQuality.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, qualities)
        )
        binding.dropdownWallpaperFps.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, frameRates)
        )

        setDropdownValue(
            binding.dropdownWallpaperStyle,
            styles,
            preferences.getString(PREF_WALLPAPER_STYLE, styles[0])!!
        ) { value ->
            preferences.edit().putString(PREF_WALLPAPER_STYLE, value).apply()
        }

        setDropdownValue(
            binding.dropdownWallpaperSpeed,
            speeds,
            preferences.getString(PREF_WALLPAPER_SPEED, speeds[1])!!
        ) { value ->
            preferences.edit().putString(PREF_WALLPAPER_SPEED, value).apply()
        }

        setDropdownValue(
            binding.dropdownWallpaperQuality,
            qualities,
            preferences.getString(PREF_WALLPAPER_QUALITY, qualities[1])!!
        ) { value ->
            preferences.edit().putString(PREF_WALLPAPER_QUALITY, value).apply()
        }

        setDropdownValue(
            binding.dropdownWallpaperFps,
            frameRates,
            preferences.getString(PREF_WALLPAPER_FPS, frameRates[2])!!
        ) { value ->
            preferences.edit().putString(PREF_WALLPAPER_FPS, value).apply()
        }
    }

    private fun setDropdownValue(
        dropdown: android.widget.AutoCompleteTextView,
        values: Array<String>,
        savedValue: String,
        onSelected: (String) -> Unit
    ) {
        val selectedValue = savedValue.takeIf { it in values } ?: values[0]
        dropdown.setText(selectedValue, false)
        dropdown.setOnItemClickListener { _, _, position, _ ->
            onSelected(values[position])
        }
    }

    private fun setupSwitches() {
        binding.switchWallpaperMotion.isChecked =
            preferences.getBoolean(PREF_WALLPAPER_MOTION, true)
        binding.switchWallpaperTouch.isChecked =
            preferences.getBoolean(PREF_WALLPAPER_TOUCH, true)
        binding.switchWallpaperParallax.isChecked =
            preferences.getBoolean(PREF_WALLPAPER_PARALLAX, true)
        binding.switchWallpaperStatic.isChecked =
            preferences.getBoolean(PREF_WALLPAPER_STATIC, false)

        binding.switchWallpaperMotion.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(PREF_WALLPAPER_MOTION, checked).apply()
        }
        binding.switchWallpaperTouch.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(PREF_WALLPAPER_TOUCH, checked).apply()
        }
        binding.switchWallpaperParallax.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(PREF_WALLPAPER_PARALLAX, checked).apply()
        }
        binding.switchWallpaperStatic.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(PREF_WALLPAPER_STATIC, checked).apply()
        }
    }

    private fun setInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val left = insets.left + cutout.left
            val right = insets.right + cutout.right

            val toolbarParams = binding.toolbarWallpaper.layoutParams
                as ViewGroup.MarginLayoutParams
            toolbarParams.leftMargin = left
            toolbarParams.rightMargin = right
            binding.toolbarWallpaper.layoutParams = toolbarParams

            binding.contentWallpaper.updatePadding(
                bottom = insets.bottom
            )

            windowInsets
        }
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.setNavigationVisibility(visible = false, animated = true)
        homeViewModel.setStatusBarShadeVisibility(visible = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
