package com.harsh.desicrics.service

import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.store.ImageStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** Profile photos and team logos. The app resizes them first; this keeps them small and checks who may set them. */
class ImageService(
    private val images: ImageStore,
    private val users: UserStore,
    private val teams: TeamStore,
    private val teamService: TeamService,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun setProfilePhoto(user: FirebasePrincipal, contentType: String, bytes: ByteArray): UserProfile {
        val profile = users.get(user.uid) ?: throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
        val updated = profile.copy(photoUrl = save(user, contentType, bytes))
        users.upsert(updated)
        return updated
    }

    suspend fun setTeamLogo(user: FirebasePrincipal, teamId: String, contentType: String, bytes: ByteArray): SavedTeam {
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        if (team.roleOf(user.uid) != TeamRole.ADMIN) throw ApiException(HttpStatusCode.Forbidden, "Only team admins can do that")
        teams.setLogo(teamId, save(user, contentType, bytes))
        return teamService.get(user, teamId)
    }

    suspend fun get(id: String): Pair<String, ByteArray> =
        images.get(id) ?: throw ApiException(HttpStatusCode.NotFound, "Image not found")

    /** Saves the picture and returns the path it's served at. */
    private suspend fun save(user: FirebasePrincipal, contentType: String, bytes: ByteArray): String {
        if (contentType !in TYPES) throw ApiException(HttpStatusCode.UnsupportedMediaType, "Use a JPEG, PNG or WebP picture")
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, "Pictures must be under 1 MB")
        val id = UUID.randomUUID().toString()
        images.save(id, user.uid, contentType, bytes, clock())
        return "/images/$id"
    }

    private companion object {
        val TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_BYTES = 1_000_000
    }
}
