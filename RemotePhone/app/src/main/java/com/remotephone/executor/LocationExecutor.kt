package com.remotephone.executor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Executor that gets the device's current GPS location
 * using FusedLocationProviderClient.
 */
class LocationExecutor(private val context: Context) : CommandExecutor {

    companion object {
        private const val TAG = "LocationExecutor"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun execute(command: Command): CommandResult {
        return try {
            // Try to get current location with high accuracy
            val cancellationToken = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                val accuracy = location.accuracy
                val altitude = location.altitude
                val speed = location.speed

                // Try to get address from coordinates
                val address = try {
                    getAddressFromCoordinates(latitude, longitude)
                } catch (e: Exception) {
                    null
                }

                val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"

                val result = buildString {
                    appendLine("📍 Location")
                    appendLine("Latitude: ${"%.6f".format(latitude)}")
                    appendLine("Longitude: ${"%.6f".format(longitude)}")
                    appendLine("Accuracy: ${"%.1f".format(accuracy)}m")
                    if (altitude != 0.0) appendLine("Altitude: ${"%.1f".format(altitude)}m")
                    if (speed > 0) appendLine("Speed: ${"%.1f".format(speed * 3.6)} km/h")
                    if (address != null) appendLine("📫 Address: $address")
                    appendLine("🗺️ Maps: $mapsUrl")
                }

                Log.d(TAG, "Location: $latitude, $longitude")

                CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = result.trim()
                )
            } else {
                // Fallback to last known location
                val lastLocation = fusedLocationClient.lastLocation.await()
                if (lastLocation != null) {
                    val mapsUrl = "https://maps.google.com/?q=${lastLocation.latitude},${lastLocation.longitude}"
                    CommandResult(
                        commandId = command.id,
                        type = ResultType.TEXT,
                        data = "📍 Last Known Location\nLat: ${lastLocation.latitude}\nLng: ${lastLocation.longitude}\n🗺️ $mapsUrl\n\n⚠️ This is the last known location, not current."
                    )
                } else {
                    CommandResult(
                        commandId = command.id,
                        type = ResultType.TEXT,
                        data = "Error: Unable to get location. Make sure GPS is enabled."
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: Location permission not granted"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getAddressFromCoordinates(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.let { address ->
                buildString {
                    for (i in 0..address.maxAddressLineIndex) {
                        if (i > 0) append(", ")
                        append(address.getAddressLine(i))
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
