package com.kotlinconf.library.domain.repository

import com.github.aakira.napier.Napier
import dev.icerock.moko.mvvm.livedata.LiveData
import dev.icerock.moko.mvvm.livedata.MutableLiveData
import dev.icerock.moko.mvvm.livedata.readOnly
import dev.icerock.moko.network.generated.apis.GameApi
import dev.icerock.moko.network.generated.models.ConfigResponse
import dev.icerock.moko.network.generated.models.ProximityResponse
import dev.icerock.moko.network.generated.models.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kotlinconf.library.domain.UI
import com.kotlinconf.library.domain.entity.BeaconInfo
import com.kotlinconf.library.domain.entity.GameConfig
import com.kotlinconf.library.domain.entity.ProximityInfo
import com.kotlinconf.library.domain.entity.toDomain
import com.kotlinconf.library.domain.storage.KeyValueStorage
import com.kotlinconf.library.domain.storage.PersistentCookiesStorage

class GameDataRepository internal constructor(
    private val gameApi: GameApi,
    private val collectedSpotsRepository: CollectedSpotsRepository,
    private val storage: KeyValueStorage,
    private val cookiesStorage: PersistentCookiesStorage,
    private val watchSyncRepository: WatchSyncRepository
) {
    val beaconsChannel: Channel<BeaconInfo> = Channel(Channel.BUFFERED)
    var gameConfig: GameConfig? = null

    private val _currentDiscoveredBeaconId: MutableLiveData<Int?> = MutableLiveData<Int?>(null)
    val currentDiscoveredBeaconId: LiveData<Int?> = this._currentDiscoveredBeaconId.readOnly()

    private val _isGameEnded: MutableLiveData<Boolean> = MutableLiveData(false)
    val isGameEnded: LiveData<Boolean> = this._isGameEnded.readOnly()

    private val _proximityInfoChannel: Channel<ProximityInfo?> = Channel(Channel.BUFFERED)
    val proximityInfo: Flow<ProximityInfo?> = channelFlow {
        val job = launch {
            while (isActive) {
                val info: ProximityInfo? = _proximityInfoChannel.receive()
                send(info)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    fun startReceivingData() {
        var isFirstRun: Boolean = true

        GlobalScope.launch(Dispatchers.UI) {
            while (isActive) {
                val scanResults = mutableListOf<BeaconInfo>()
                var beacon = beaconsChannel.poll()
                while (beacon != null) {
                    scanResults.add(beacon)
                    beacon = beaconsChannel.poll()
                }

                if (scanResults.isNotEmpty()) {
                    async {
                        if (_isGameEnded.value) return@async

                        val info: ProximityInfo? = sendBeaconsInfo(scanResults)
                        val config = gameConfig

                        if (config == null) {
                            Napier.e("game config is null")
                            return@async
                        }

                        _proximityInfoChannel.send(info)

                        val collectedIds: List<Int> = collectedSpotsRepository.collectedSpotIds() ?: emptyList()
                        val discoveredIds: List<Int> = info?.discoveredBeaconsIds ?: emptyList()

                        val newIds: List<Int> = discoveredIds.minus(collectedIds)

                        Napier.d("collected: $collectedIds, discovered: $discoveredIds, new: $newIds")

                        if (!isFirstRun)
                            _currentDiscoveredBeaconId.value = newIds.firstOrNull()
                        else
                            isFirstRun = false

                        collectedSpotsRepository.setCollectedSpotIds(discoveredIds)

                        _isGameEnded.value = (discoveredIds.count() == config.winnerCount)

                        watchSyncRepository.sendData(
                            info?.discoveredBeaconsIds?.size ?: 0,
                            info?.nearestBeaconStrength,
                            _currentDiscoveredBeaconId.value,
                            _isGameEnded.value
                        )
                    }
                }

                delay(1000)
            }
        }
    }

    fun isUserRegistered(): Boolean {
        return this.storage.isUserRegistered
    }

    fun setUserRegistered(registered: Boolean) {
        this.storage.isUserRegistered = registered
    }

    fun resetCookies() {
        this.storage.cookies = null
        this.storage.isUserRegistered = false

        this.cookiesStorage.banCookie()

        this.collectedSpotsRepository.setCollectedSpotIds(emptyList())

        Napier.d("COOKIES CLEARED")
    }

    fun cookie(): String? {
        return this.cookiesStorage.lastCookie()
    }

    suspend fun loadGameConfig(): GameConfig? {
        val config: ConfigResponse = this.gameApi.finderConfigGet()
        Napier.d(message = "Game config response = $config")

        this.gameConfig = config.toDomain()

        return this.gameConfig
    }

    suspend fun sendWinnerName(name: String): String? {
        storage.winnerName = name

        val response: RegisterResponse = this.gameApi.finderRegisterGet(name)
        Napier.d(message = "Register response: $response")

        return response.message
    }

    private suspend fun sendBeaconsInfo(beacons: List<BeaconInfo>): ProximityInfo? {
        val onlyLast = beacons
            .filter { it.rssi < 0 }
            .reversed()
            .distinctBy { it.name }

        if (onlyLast.isEmpty()) {
            Napier.d(message = "all filtered = $beacons")
            return null
        }

        val beaconsString: String =
            onlyLast.joinToString(separator = ",") { "${it.name.cityHash64().toString(16)}:${it.rssi}" }

//        Napier.d(message = "proximity = $beaconsString")

        val info: ProximityInfo

        try {
            val response: ProximityResponse = this.gameApi.finderProximityGet(beaconsString)
//            Napier.d(message = "received = $response")

            info = response.toDomain()
        } catch (error: Throwable) {
            Napier.e(message = "can't get proximity", throwable = error)
            Napier.e(message = error.toString())
            return null
        }

        return info
    }
}
