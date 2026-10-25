package com.remotephone.executor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType

/**
 * Executor that reads battery information: level, charging status,
 * power source, temperature, and voltage.
 */
class BatteryExecutor(private val context: Context) : CommandExecutor {

    companion object {
        private const val TAG = "BatteryExecutor"
    }

    override suspend fun execute(command: Command): CommandResult {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }

            if (batteryStatus == null) {
                return CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "Error: Unable to read battery status"
                )
            }

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val powerSource = when (chargePlug) {
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Not charging"
            }

            val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempCelsius = if (temperature > 0) temperature / 10.0 else 0.0

            val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageV = if (voltage > 0) voltage / 1000.0 else 0.0

            val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthStatus = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val result = buildString {
                appendLine("🔋 Battery: $percentage%")
                appendLine("⚡ Status: $statusText")
                appendLine("🔌 Power Source: $powerSource")
                appendLine("🌡️ Temperature: ${tempCelsius}°C")
                appendLine("⚡ Voltage: ${voltageV}V")
                appendLine("❤️ Health: $healthStatus")
            }

            Log.d(TAG, "Battery info: $percentage%, $statusText")

            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = result.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read battery", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }
}
