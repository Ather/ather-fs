package dev.ather.fs.drive

import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.UserPrincipal

sealed class DriveUserPrincipal : UserPrincipal, GroupPrincipal {

    abstract val principalName: String

    override fun getName(): String = principalName

    data class OAuth2Principal(
        override val principalName: String,
        val accessToken: String,
        val expirationTimeMilliseconds: Long,
        val refreshToken: String
    ) : DriveUserPrincipal()

    data class ServiceAccountPrincipal(
        override val principalName: String,
        val privateKeyId: String,
        val privateKeyPkcs8: String,
        val clientEmail: String
    ) : DriveUserPrincipal()
}
