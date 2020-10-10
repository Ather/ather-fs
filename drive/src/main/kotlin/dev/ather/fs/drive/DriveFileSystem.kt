package dev.ather.fs.drive

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.PemReader
import com.google.api.client.util.SecurityUtils
import com.google.api.services.drive.Drive
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.auth.oauth2.UserCredentials
import dev.ather.fs.drive.util.GlobUtil
import dev.ather.fs.drive.util.PaginatedIterable
import dev.ather.fs.drive.util.executeBackoff
import java.io.StringReader
import java.nio.file.FileSystem
import java.nio.file.PathMatcher
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class DriveFileSystem(
    private val fileSystemProvider: DriveFileSystemProvider,
    private val principalLookupService: DriveUserPrincipalLookupService<*>,
    private val environment: DriveEnvironment,
    internal val principalName: String
) : FileSystem() {

    private fun String.toPkcs8PrivateKey(): PrivateKey? = try {
        val reader = StringReader(this)
        val privateKeySection = PemReader.readFirstSectionAndClose(reader, "PRIVATE KEY")!!
        val keySpec = PKCS8EncodedKeySpec(privateKeySection.base64DecodedBytes)
        SecurityUtils.getRsaKeyFactory().generatePrivate(keySpec)
    } catch (t: Throwable) {
        null
    }

    private fun UserCredentials.Builder.withSecrets(secrets: OAuth2Secrets) = apply {
        clientId = secrets.clientId
        clientSecret = secrets.clientSecret
    }

    private fun ServiceAccountCredentials.Builder.withServiceAccountCredentials(credentials: DriveUserPrincipal.ServiceAccountPrincipal) =
        apply {
            clientEmail = credentials.clientEmail
            scopes = emptyList()
            privateKey = credentials.privateKeyPkcs8.toPkcs8PrivateKey()
            privateKeyId = credentials.privateKeyId
        }

    private fun UserCredentials.Builder.withToken(credential: DriveUserPrincipal.OAuth2Principal) = apply {
        accessToken = AccessToken(credential.accessToken, Date(credential.expirationTimeMilliseconds))
        refreshToken = credential.refreshToken
    }

    private val credential: GoogleCredentials by lazy {
        val credential = principalLookupService[principalName]
        when (principalLookupService) {
            is DriveUserPrincipalLookupService.DriveOAuth2PrincipalLookupService -> when (credential) {
                is DriveUserPrincipal.OAuth2Principal -> UserCredentials.newBuilder()
                    .withSecrets(principalLookupService.secrets)
                    .withToken(credential)
                    .build()
                is DriveUserPrincipal.ServiceAccountPrincipal -> ServiceAccountCredentials.newBuilder()
                    .withServiceAccountCredentials(credential)
                    .build()
                else -> GoogleCredentials.newBuilder().build()
            }
            is DriveUserPrincipalLookupService.DriveServiceAccountPrincipalLookupService -> (credential as? DriveUserPrincipal.ServiceAccountPrincipal)?.let {
                ServiceAccountCredentials.newBuilder()
                    .withServiceAccountCredentials(it)
                    .build()
            } ?: GoogleCredentials.newBuilder().build()
            else -> GoogleCredentials.newBuilder().build()
        }
    }

    private val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()

    internal val driveApi: Drive by lazy {
        Drive.Builder(httpTransport, JSON_FACTORY) {
            it.headers.putAll(credential.requestMetadata)
        }.apply {
            applicationName = APPLICATION_NAME
        }.build()
    }

    override fun getSeparator(): String = "/"

    override fun newWatchService(): DriveWatchService = DriveWatchService(this)

    override fun supportedFileAttributeViews(): Set<String> = setOf("basic") // TODO other views

    override fun isReadOnly(): Boolean = false // TODO Check if Drive permission is a readOnly scope

    override fun getFileStores(): Iterable<DriveFileStore> = PaginatedIterable<DriveFileStore>(
        initialValues = listOf(DriveFileStore.MyDrive)
    ) { nextPageToken ->
        driveApi.drives().list()
            .setPageToken(nextPageToken)
            .executeBackoff(environment).run {
                PaginatedIterable.PaginatedIterableResult(
                    drives.map { DriveFileStore.SharedDrive(it.name, it.id) },
                    nextPageToken
                )
            }
    }

    override fun getPath(first: String, vararg more: String): DrivePath = DrivePath(this, principalName, first, *more)

    override fun provider(): DriveFileSystemProvider = fileSystemProvider

    override fun isOpen(): Boolean = true

    override fun getUserPrincipalLookupService(): DriveUserPrincipalLookupService<*> = principalLookupService

    override fun close() = httpTransport.shutdown()

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        val type = syntaxAndPattern.substringBefore(':')
        val pattern = syntaxAndPattern.substringAfter(':')
        val regex = when (type) {
            "glob" -> GlobUtil.convertGlobToRegEx(pattern)
            else -> pattern.toRegex()
        }
        return PathMatcher { path -> regex.matches(path.toString()) }
    }

    override fun getRootDirectories(): Iterable<DrivePath> = fileStores.asSequence().map {
        DrivePath(
            this,
            principalName,
            rootId = it.rootId
        )
    }.asIterable()

    companion object {
        private const val APPLICATION_NAME = "ather-fs"

        private val JSON_FACTORY: JacksonFactory = JacksonFactory.getDefaultInstance()
    }
}
