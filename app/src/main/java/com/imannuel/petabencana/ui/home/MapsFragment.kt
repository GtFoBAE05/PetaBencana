package com.imannuel.petabencana.ui.home

import android.app.DatePickerDialog
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.search.SearchView
import com.imannuel.petabencana.R
import com.imannuel.petabencana.databinding.FragmentMapsBinding
import com.imannuel.petabencana.ui.home.adapter.LatestDisasterAdapter
import com.imannuel.petabencana.utils.AreaList
import com.imannuel.petabencana.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@AndroidEntryPoint
class MapsFragment : Fragment() {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LatestDisasterAdapter

    private val selectedCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?

        setupAdapter()
        setupChips()
        setupSearchBar()
        setupSearchView()

        mapFragment?.getMapAsync { googleMap ->

            if (setupMapStyle()) {
                googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                        requireContext(),
                        R.raw.map_style
                    )
                )
            }

            viewModel.result.observe(viewLifecycleOwner) { result ->
                when (result) {
                    is Resource.Success -> {
                        googleMap.clear()
                        setLoading(false)
                        val boundsBuilder = LatLngBounds.Builder()

                        if (!result.data.result?.objects?.output?.geometries.isNullOrEmpty()) {
                            result.data.result?.objects?.output?.geometries?.forEach {
                                val lat = it.coordinates[1]
                                val lng = it.coordinates[0]
                                val latLng = LatLng(lat, lng)

                                val customMarkerIcon = when (it.properties?.disasterType) {
                                    "flood" -> BitmapDescriptorFactory.fromResource(R.drawable.flood)
                                    "earthquake" -> BitmapDescriptorFactory.fromResource(R.drawable.earthquakes)
                                    "fire" -> BitmapDescriptorFactory.fromResource(R.drawable.fire)
                                    "haze" -> BitmapDescriptorFactory.fromResource(R.drawable.haze)
                                    "wind" -> BitmapDescriptorFactory.fromResource(R.drawable.wind)
                                    else -> BitmapDescriptorFactory.fromResource(R.drawable.volcano)
                                }
                                googleMap.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title(getAddress(latLng))
                                        .icon(customMarkerIcon)
                                )
                                boundsBuilder.include(latLng)
                            }

                            val bounds: LatLngBounds = boundsBuilder.build()
                            googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(
                                    bounds,
                                    resources.displayMetrics.widthPixels,
                                    resources.displayMetrics.heightPixels,
                                    300
                                )
                            )

                            adapter.setData(result.data.result?.objects?.output?.geometries!!)
                        } else {
                            adapter.setData(emptyList())
                            Toast.makeText(
                                requireContext(),
                                "No disaster found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }


                    }

                    is Resource.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Error",
                            Toast.LENGTH_SHORT
                        ).show()

                        googleMap.clear()
                        setLoading(false)

                    }

                    is Resource.Loading -> {
                        setLoading(true)
                    }
                }
            }
        }


    }

    private fun setupAdapter() {
        adapter = LatestDisasterAdapter {

            val bundle = Bundle()
            bundle.putParcelable("key_data", it.properties)
            bundle.putDouble("lat", it.coordinates[1])
            bundle.putDouble("lon", it.coordinates[0])

            findNavController().navigate(R.id.action_mapsFragment_to_savedDetailFragment, bundle)
        }
        recyclerView = binding.latestDisasterRv
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupChips() {
        for (i in 0 until binding.disasterChipGroup.childCount) {
            val chip: Chip = binding.disasterChipGroup.getChildAt(i) as Chip

            chip.setOnCheckedChangeListener { selectedChip, isChecked ->
                if (isChecked) {
                    viewModel.setDisaster(
                        selectedChip.text.toString()
                            .lowercase(Locale.getDefault())
                    )
                } else {
                    viewModel.setDisaster("")
                }
            }

        }
    }

    private fun setupSearchBar() {
        binding.searchBar.inflateMenu(R.menu.main_menu)
        binding.searchBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_settings -> {
                    findNavController().navigate(R.id.action_mapsFragment_to_settingFragment)
                    true
                }

                R.id.menu_period -> {
                    showDatePickerDialog()
                    true
                }

                R.id.menu_saved -> {
                    findNavController().navigate(R.id.action_mapsFragment_to_savedFragment)
                    true
                }

                else -> {
                    true
                }

            }
        }
    }

    private fun setupMapStyle(): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        preferences.getString("darkModePreferenceKey", "auto").apply {
            return when (this) {
                "off" -> false
                "on" -> true
                else -> {
                    val uiModeManager =
                        requireContext().getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    val nightModeFlags = uiModeManager.nightMode
                    return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
    }

    private fun setupSearchView() {
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, AreaList.dataList)
        binding.areaListView.adapter = adapter



        binding.sv.addTransitionListener { searchView, _, newState ->
            if (newState == SearchView.TransitionState.SHOWING) {
                searchView.editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        adapter.filter.filter(s.toString())
                    }

                    override fun afterTextChanged(s: Editable?) {
                    }

                })
            }
        }

        binding.sv
            .editText
            .setOnEditorActionListener { _, _, _ ->
                binding.searchBar.text = binding.sv.text
                binding.sv.hide()
                viewModel.setArea(AreaList.getAreaId(binding.sv.text.toString()))
                false
            }


        binding.areaListView.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = adapter.getItem(position)
            binding.sv.setText(selectedItem.toString())
            binding.searchBar.text = binding.sv.text
            binding.sv.hide()
            viewModel.setArea(AreaList.getAreaId(binding.sv.text.toString()))
        }
    }

    private fun getAddress(latLng: LatLng): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                return address.getAddressLine(0)
            }

            return ""
        } catch (e: Exception) {
            return ""
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->

                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                val resultInSeconds =
                    abs((selectedCalendar.timeInMillis - calendar.timeInMillis) / 1000).toInt()

                if (resultInSeconds > 604800) {
                    viewModel.setTimePeriod(604800)
                } else {
                    viewModel.setTimePeriod(resultInSeconds)
                }

            },
            year,
            month,
            day
        )

        val minDate = Calendar.getInstance()
        minDate.add(Calendar.DAY_OF_MONTH, -7)

        datePickerDialog.datePicker.minDate = minDate.timeInMillis
        datePickerDialog.datePicker.maxDate = calendar.timeInMillis

        datePickerDialog.setTitle("Select period before today")

        datePickerDialog.datePicker.updateDate(
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun setLoading(bool: Boolean) {
        if (bool) {
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }


}