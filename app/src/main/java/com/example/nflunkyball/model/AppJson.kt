package com.example.nflunkyball.model

import kotlinx.serialization.json.Json

/** The app's two JSON configurations, so no call site picks flags ad hoc. */
object AppJson {
    /** For anything read back from disk or the wire: unknown keys (from a newer app or backend)
     *  are ignored rather than fatal; defaults are written out explicitly so files and uploads
     *  are self-describing. */
    val lenient: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** For the BLE broadcast only: every default-valued field omitted shrinks the chunk count on
     *  that bandwidth-starved transport; decoding still fills defaults back in regardless. */
    val compact: Json = Json { ignoreUnknownKeys = true }
}
