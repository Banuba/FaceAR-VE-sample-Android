package com.banuba.sdk.example

import com.banuba.sdk.cameraui.data.CameraTimerStateProvider
import com.banuba.sdk.cameraui.data.TimerEntry
import com.banuba.sdk.core.AREffectPlayerProvider
import com.banuba.sdk.core.IUtilityManager
import com.banuba.sdk.effectplayer.adapter.BanubaAREffectPlayerProvider
import com.banuba.sdk.effectplayer.adapter.BanubaClassFactory
import com.banuba.sdk.ve.flow.ExportFlowManager
import com.banuba.sdk.ve.flow.ExportResultHandler
import com.banuba.sdk.ve.flow.FlowEditorModule
import org.koin.core.definition.BeanDefinition
import org.koin.core.qualifier.named

class VideoEditorKoinModule : FlowEditorModule() {

    override val effectPlayerManager: BeanDefinition<AREffectPlayerProvider> =
        single(override = true) {
            BanubaAREffectPlayerProvider(
                mediaSizeProvider = get(),
                token = "ippJfY0xiKGOqWDm0uFnayxK7fK+1u0DuoAruXX1Ka54R5Qjwj4s9ZoGaGaACFfgi0HAL32wzC1slfUqZ6yyxXpxQ+ljiL+/hFpStDMmBWGZBCvG+FlTtd9wa9bU9rC6DjJwNuRfGSe4bszdKxGBEPE5MY5nJW76w/ffFeY6U898IOiOVy5wmsqXERSEbFA9PAeBYLnRpAx13CSFKWeTONMde5dMl3jmnLMlfXkbMXve9gTvL/fnDTyyUDeWHX6YfbmmWHFpP24t"
            )
        }

    override val utilityManager: BeanDefinition<IUtilityManager> = single(override = true) {
        BanubaClassFactory.createUtilityManager(
            context = get()
        )
    }

    override val exportFlowManager: BeanDefinition<ExportFlowManager> = single {
        VideoEditorExportFlowManager(
            exportDataProvider = get(),
            editorSessionHelper = get(),
            exportDir = get(named("exportDir")),
            mediaFileNameHelper = get()
        )
    }

    override val exportResultHandler: BeanDefinition<ExportResultHandler> = single {
        VideoEditorExportResultHandler()
    }

    override val cameraTimerStateProvider: BeanDefinition<CameraTimerStateProvider> =
        factory {
            object : CameraTimerStateProvider {
                override val timerStates: List<TimerEntry> = emptyList()
            }
        }
}