package fr.leboncoin.androidrecruitmenttestapp.di

import fr.leboncoin.androidrecruitmenttestapp.utils.AnalyticsHelper
import fr.leboncoin.data.di.DataDependencies
import java.util.logging.Logger

interface AppDependenciesProvider {
    val dependencies: AppDependencies
}

class AppDependencies {
    val logger: Logger by lazy { Logger.getGlobal() }
    val analyticsHelper: AnalyticsHelper by lazy { AnalyticsHelper() }
    val dataDependencies: DataDependencies by lazy { DataDependencies() }
}