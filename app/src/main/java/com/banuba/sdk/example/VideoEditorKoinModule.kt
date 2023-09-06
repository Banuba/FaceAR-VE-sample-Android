package com.banuba.sdk.example

import android.app.Application
import com.banuba.sdk.arcloud.data.source.ArEffectsRepositoryProvider
import com.banuba.sdk.audiobrowser.domain.AudioBrowserMusicProvider
import com.banuba.sdk.cameraui.domain.HandsFreeTimerActionProvider
import com.banuba.sdk.core.domain.DraftConfig
import com.banuba.sdk.export.data.ForegroundExportFlowManager
import com.banuba.sdk.veui.domain.CoverProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

class VideoEditorKoinModule {

    val module = module {

        single {
            ForegroundExportFlowManager(
                exportDataProvider = get(),
                exportSessionHelper = get(),
                exportDir = get(named("exportDir")),
                shouldClearSessionOnFinish = true,
                publishManager = get(),
                errorParser = get(),
                exportBundleProvider = get(),
                eventConverter = get()
            )
        }

        single {
            ExternalPublishManager(
                context = get<Application>().applicationContext,
                albumName = "Banuba",
                mediaFileNameHelper = get(),
                dispatcher = get(named("ioDispatcher"))
            )
        }

        single(createdAtStart = true) {
            ArEffectsRepositoryProvider(
                arEffectsRepository = get(named("backendArEffectsRepository")),
                ioDispatcher = get(named("ioDispatcher"))
            )
        }

        single(named("musicTrackProvider")) {
            AudioBrowserMusicProvider()
        }

        single {
            CoverProvider.EXTENDED
        }


        single {
            HandsFreeTimerActionProvider()
        }

        factory {
            DraftConfig.ENABLED_ASK_TO_SAVE
        }
    }
}
