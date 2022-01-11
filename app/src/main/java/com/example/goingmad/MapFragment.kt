package com.example.goingmad

import android.accounts.NetworkErrorException
import android.content.Context
import android.location.Address
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.collect.MapMaker
import kotlinx.coroutines.*
import org.mapsforge.core.model.Tile.getBoundingBox
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var map : MapView
    private lateinit var controller: IMapController
    var job: Job? = null
    val args: MapFragmentArgs by navArgs()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        map=view.findViewById(R.id.bigMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        val abortMap=view.findViewById<Button>(R.id.abortMap)
        val confirmMap=view.findViewById<Button>(R.id.confirmMap)
        val locationMap=view.findViewById<TextView>(R.id.locationMap)

        Configuration.getInstance().load(this.requireContext(), activity?.getSharedPreferences("map", Context.MODE_PRIVATE))

        controller = map.controller
        val mapPoint = GeoPoint(42.7443, 12.0809)
        controller.setZoom(4.5)
        controller.animateTo(mapPoint)

        if(args.localityToReturn>=0){
                val mReceive: MapEventsReceiver = object : MapEventsReceiver {

                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        if (map.overlays.size > 1){
                            map.overlays.remove(map.overlayManager.last())
                            map.invalidate()
                        }
                        var result = ""
                        val mapUtilities = MapUtilities()
                        val marker = Marker(map)
                        marker.icon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.goingmadmarker)
                        marker.position = p
                        if (job == null || job?.isActive == false) {
                            job = MainScope().launch {
                                result = mapUtilities.getLocation(p)
                            }
                        }
                        job?.invokeOnCompletion {
                            marker.title = result
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            map.overlays.add(marker)
                            map.invalidate()
                            locationMap.visibility = View.VISIBLE
                            abortMap.visibility = View.VISIBLE
                            confirmMap.visibility = View.VISIBLE
                            locationMap.text =
                                if (result!="")
                                     {
                                locationMap.visibility = View.VISIBLE
                                abortMap.visibility = View.VISIBLE
                                confirmMap.visibility = View.VISIBLE
                                         confirmMap.setOnClickListener {
                                             val send = args.localityToReturn.toString()+"---"+result
                                             findNavController().previousBackStackEntry?.savedStateHandle?.set("mapResult",send)
                                             findNavController().popBackStack()
                                         }
                                         abortMap.setOnClickListener {
                                             map.overlays.remove(marker)
                                             abortMap.visibility = View.INVISIBLE
                                             confirmMap.visibility = View.INVISIBLE
                                             locationMap.visibility = View.INVISIBLE
                                             map.invalidate()
                                         }
                                result}
                            else
                                getString(R.string.no_location_selected)


                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint): Boolean {
                        if (map.overlays.size > 0) map.overlays.remove(map.overlayManager.last())
                        map.invalidate()
                        return true
                    }
                }
                map.overlays.add(MapEventsOverlay(mReceive))



        }else {
            val stops = arrayListOf<GeoPoint>()

            for (m in markers) {
                val marker = Marker(map)
                marker.icon = ContextCompat.getDrawable(this.requireContext(), R.drawable.goingmadmarker)
                marker.position = m.position
                marker.title = m.title
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                stops.add(m.position)
                map.overlays.add(marker)
            }





            if (job == null || job?.isActive == false) {
                job = MainScope().launch {
                    //Create road line
                    val mapUpdater = MapUpdater(requireContext(), map, view)
                    val roadLine = mapUpdater.createRoad(stops)
                    // Add road line to the map
                    map.overlays.add(roadLine)
                    if(markers.size>1) {
                        val b: BoundingBox = getBoundingBox(stops[0], stops[stops.size - 1])
                        map.zoomToBoundingBox(b, false, 100)
                    }
                    else{
                        controller.setZoom(5.5)
                        controller.animateTo(markers[0].position)
                    }
                    // Update Map
                    map.invalidate()
                }
            }
        }
    }

    private fun getBoundingBox(start: GeoPoint, end: GeoPoint): BoundingBox {
        val north: Double
        val south: Double
        val east: Double
        val west: Double
        if (start.latitude > end.latitude) {
            north = start.latitude
            south = end.latitude
        } else {
            north = end.latitude
            south = start.latitude
        }
        if (start.longitude > end.longitude) {
            east = start.longitude
            west = end.longitude
        } else {
            east = end.longitude
            west = start.longitude
        }
        return BoundingBox(north, east, south, west)
    }

    override fun onResume() {
        map.onResume()
        super.onResume()
    }

    override fun onPause() {
        job?.cancel()
        map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}

class MapUtilities(){

    suspend fun getLocation (geoPoint: GeoPoint): String{

        return withContext(Dispatchers.IO){
            var result = ""
            val coderNominatim = GeocoderNominatim("madcarpooling")
            try {
                val geoResults: List<Address> = coderNominatim.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                if (geoResults.isNotEmpty()){
                    result = geoResults[0].locality + " " + (geoResults[0].thoroughfare?: "")
                }
            }
            catch (e : IOException){

            }
            return@withContext result
        }

    }

    suspend fun locationExist(locality: String):Boolean {
        return withContext(Dispatchers.IO){
            var result= false
            val coderNominatim = GeocoderNominatim("madcarpooling")
            try {
                val geoResults: List<Address> = coderNominatim.getFromLocationName(locality, 1)
                if (geoResults.isNotEmpty()) {
                    result = true
                }
            }
            catch(e: IOException){

            }
            return@withContext  result
        }


    }
}
