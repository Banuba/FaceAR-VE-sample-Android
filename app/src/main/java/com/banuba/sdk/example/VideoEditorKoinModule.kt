package com.banuba.sdk.example

import android.app.Application
import androidx.fragment.app.Fragment
import com.banuba.sdk.arcloud.data.source.ArEffectsRepositoryProvider
import com.banuba.sdk.audiobrowser.domain.AudioBrowserMusicProvider
import com.banuba.sdk.cameraui.data.CameraTimerActionProvider
import com.banuba.sdk.cameraui.data.CameraTimerStateProvider
import com.banuba.sdk.cameraui.domain.HandsFreeTimerActionProvider
import com.banuba.sdk.core.data.TrackData
import com.banuba.sdk.core.domain.DraftConfig
import com.banuba.sdk.core.ui.ContentFeatureProvider
import com.banuba.sdk.export.data.ExportFlowManager
import com.banuba.sdk.export.data.ForegroundExportFlowManager
import com.banuba.sdk.ve.data.PublishManager
import com.banuba.sdk.ve.effects.WatermarkProvider
import com.banuba.sdk.ve.flow.ExportResultHandler
import com.banuba.sdk.ve.flow.FlowEditorModule
import com.banuba.sdk.veui.domain.CoverProvider
import org.koin.core.definition.BeanDefinition
import org.koin.core.qualifier.named

class VideoEditorKoinModule : FlowEditorModule() {

    val exportFlowManager: BeanDefinition<ExportFlowManager> = single(override = true) {
        ForegroundExportFlowManager(
            exportDataProvider = get(),
            sessionParamsProvider = get(),
            exportSessionHelper = get(),
            exportDir = get(named("exportDir")),
            shouldClearSessionOnFinish = true,
            publishManager = get(),
            errorParser = get(),
            mediaFileNameHelper = get()
        )
    }

    val publishManager: BeanDefinition<PublishManager> = single(override = true) {
        ExternalPublishManager(
            context = get<Application>().applicationContext,
            albumName = "Banuba",
            mediaFileNameHelper = get(),
            dispatcher = get(named("ioDispatcher"))
        )
    }

    val arEffectsRepositoryProvider: BeanDefinition<ArEffectsRepositoryProvider> =
        single(override = true, createdAtStart = true) {
            ArEffectsRepositoryProvider(
                arEffectsRepository = get(named("backendArEffectsRepository")),
                ioDispatcher = get(named("ioDispatcher"))
            )
        }

    override val cameraTimerStateProvider: BeanDefinition<CameraTimerStateProvider> =
        factory(override = true) {
            IntegrationTimerStateProvider()
        }

    override val musicTrackProvider: BeanDefinition<ContentFeatureProvider<TrackData, Fragment>> =
        single(named("musicTrackProvider"), override = true) {
            AudioBrowserMusicProvider()
        }

    override val coverProvider: BeanDefinition<CoverProvider> = single(override = true) {
        CoverProvider.EXTENDED
    }

    override val cameraTimerActionProvider: BeanDefinition<CameraTimerActionProvider> =
        single(override = true) {
            HandsFreeTimerActionProvider()
        }

    override val draftConfig: BeanDefinition<DraftConfig> = factory(override = true) {
        DraftConfig.ENABLED_ASK_TO_SAVE
    }
}
