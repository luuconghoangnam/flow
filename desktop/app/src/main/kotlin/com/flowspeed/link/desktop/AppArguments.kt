package com.flowspeed.link.desktop

data class AppArguments(
    val getIntegrationPort: Boolean,
    val startIfNotStarted: Boolean,
    val startSilent: Boolean,
    val debug: Boolean,
    val version: Boolean,
    val exit: Boolean,
    /** URL to immediately open in add-download dialog (passed by Go service). */
    val addDownloadUrl: String? = null,
) {
    companion object {
        private lateinit var instance: AppArguments
        fun get() = instance

        /**
         * Initial me on app startup
         */
        fun init(args: Array<String>) {
            instance = create(args)
        }

        private fun create(args: Array<String>): AppArguments {
            // Parse --add-download <url>
            val addDownloadIdx = args.indexOf(Args.ADD_DOWNLOAD)
            val addDownloadUrl = if (addDownloadIdx >= 0 && addDownloadIdx + 1 < args.size) {
                args[addDownloadIdx + 1]
            } else null

            return AppArguments(
                getIntegrationPort = args.contains(Args.GET_INTEGRATION_PORT),
                startIfNotStarted = args.contains(Args.START_IF_NOT_STARTED),
                startSilent = args.contains(Args.BACKGROUND),
                debug = args.contains(Args.DEBUG),
                version = args.contains(Args.VERSION),
                exit = args.contains(Args.EXIT),
                addDownloadUrl = addDownloadUrl,
            )
        }
    }

    object Args {
        const val START_IF_NOT_STARTED = "--start-if-not-started"
        const val BACKGROUND = "--background"
        const val GET_INTEGRATION_PORT = "--get-integration-port"
        const val DEBUG = "--debug"
        const val VERSION = "--version"
        const val EXIT = "--exit"
        const val ADD_DOWNLOAD = "--add-download"
    }
}