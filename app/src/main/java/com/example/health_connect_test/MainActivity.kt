package com.example.health_connect_test

import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    private val  HEALTH_CONNECT_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        val healthConnectClient: HealthConnectClient = getHealthConnectClient() ?: return

        displayHealthConnectSDKStatus(onOKClick = {
            lifecycleScope.launch {
                requestPermission(healthConnectClient)
            }
        })
    }

    private fun displayHealthConnectSDKStatus(onOKClick: () -> Unit){
        val status = getHealthConnectSDKStatus()
        var text = "";

        when (status){
            SDK_AVAILABLE  -> {
                text = "API利用可能です"
            }
            SDK_UNAVAILABLE ->{
                text = "SDKを利用できません"
            }
            SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->{
                text = "プロバイダーがインストールされていません"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("API利用可否")
            .setTitle(text)
            .setPositiveButton("OK", DialogInterface.OnClickListener { _, _ ->
                onOKClick()
            })
            .show()
    }

    private fun getHealthConnectSDKStatus(): Int{
        // Note: ProviderNameは無くてもいい
        // AndroidManifestに入れたヘルスコネクトパッケージ名を入れている
        val providerName = "com.google.android.apps.healthdata"
        return HealthConnectClient.getSdkStatus(this, providerName)
    }

    private fun getHealthConnectClient():  HealthConnectClient?{
        if  (getHealthConnectSDKStatus() == SDK_AVAILABLE){
            return HealthConnectClient.getOrCreate(this)
        }
        return null
    }

    private fun onPermissionAcceptedAction(){
        lifecycleScope.launch{
            val healthConnectClient: HealthConnectClient = getHealthConnectClient() ?: return@launch
            val step = readStepRecord(healthConnectClient)
            Toast.makeText(this@MainActivity,  "歩数は"+step+"歩", Toast.LENGTH_LONG).show()
        }
    }

    //  NOTE: registerForActivityResultを使う場合はライフサイクルがSTARTED前に定義しないといけないため、プロパティとして定義する
    private val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
    private val requestPermissions = registerForActivityResult(requestPermissionActivityContract) {granted ->
        if(granted.containsAll(HEALTH_CONNECT_PERMISSIONS)){
            Toast.makeText(this,  "全ての権限があります", Toast.LENGTH_LONG).show()
            onPermissionAcceptedAction()
        }else{
            Toast.makeText(this,  "権限が許可されませんでした", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun requestPermission(client: HealthConnectClient){
        val granted = client.permissionController.getGrantedPermissions()

        if (granted.containsAll(HEALTH_CONNECT_PERMISSIONS)){
            Log.d("MyLog", "権限は全て付与")
            onPermissionAcceptedAction()
        }else{
            Log.d("MyLog", "権限はありません")
            requestPermissions.launch(HEALTH_CONNECT_PERMISSIONS)
        }
    }


    private suspend fun readStep(client: HealthConnectClient, startTime: LocalDateTime, endTime: LocalDateTime): Long{
        var step = 0L;
        try {
            val response  = client.readRecords(
                ReadRecordsRequest(StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    dataOriginFilter = emptySet()))
            for (data in response.records){
                step += data.count
            }
        }catch (e: Exception){
            Log.d("MyLog", "歩数取得に失敗")
        }
        return step
    }

    // NOTE:  https://developer.android.com/guide/health-and-fitness/health-connect/common-workflows/aggregate-data?hl=ja
    private suspend  fun readDurationStep(client: HealthConnectClient, startTime: LocalDateTime, endTime: LocalDateTime): Long{
        var step = 0L;
        try {
            val response  = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    timeRangeSlicer = Duration.ofDays(1),
                    // Note: Google Fitで取得したデータだけとるようにする
                    dataOriginFilter = setOf(DataOrigin("com.google.android.apps.fitness"))
                )
            )
            for (data in response){
                step += data.result[StepsRecord.COUNT_TOTAL] ?: 0L
            }

        }catch (e: Exception){
            Log.d("MyLog", "歩数取得に失敗")
        }
        return step
    }

    private suspend fun readStepRecord(client: HealthConnectClient): Long{
        val startTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
        val endTime  = LocalDateTime.of(2024, 1, 6, 0, 0, 0)

        // return readStep(client, startTime, endTime)
        return readDurationStep(client, startTime, endTime)
    }
}