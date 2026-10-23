package de.powerizzle.nflunkyball.fakes

import de.powerizzle.nflunkyball.server.Ed25519
import de.powerizzle.nflunkyball.server.InvitePayload
import de.powerizzle.nflunkyball.server.OrganizerAccount
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

fun testAccount(serverUrl: String = "https://example.test"): OrganizerAccount {
    val keys = Ed25519.generateKeyPair()
    return OrganizerAccount(accountId = 7, displayName = "Orga", serverUrl = serverUrl, privateKeySeed = keys.privateKeySeed, publicKeyBytes = keys.publicKeyBytes)
}

/** An invite code exactly as `scripts/create_invite.py` would print it. */
@OptIn(ExperimentalEncodingApi::class)
fun inviteCode(server: String = "https://example.test", token: String = "tok"): String =
    Base64.UrlSafe.encode(Json.encodeToString(InvitePayload.serializer(), InvitePayload(server, token)).encodeToByteArray())
