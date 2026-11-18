package com.harsh.desicrics.data

import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.PlayerRanking
import com.harsh.desicrics.model.TeamRanking

/** Getting matches organised: "looking for" posts, the directory, and city rankings. */
class CommunityRepository(private val api: DesiCricsApi) {
    suspend fun lookingFor(city: String, kind: LookingForKind?): List<LookingForPost> = api.lookingFor(city, kind)
    suspend fun mine(): List<LookingForPost> = api.myLookingFor()
    suspend fun post(request: LookingForRequest): LookingForPost = api.postLookingFor(request)
    suspend fun respond(postId: String, message: String, phone: String): LookingForPost = api.respond(postId, RespondRequest(message.trim(), phone.trim()))
    suspend fun close(postId: String): LookingForPost = api.closeLookingFor(postId)
    suspend fun directory(city: String, kind: DirectoryKind?): List<DirectoryEntry> = api.directory(city, kind)
    suspend fun addEntry(request: DirectoryRequest): DirectoryEntry = api.addDirectoryEntry(request)
    suspend fun removeEntry(id: String) = api.removeDirectoryEntry(id)
    suspend fun teamRankings(city: String): List<TeamRanking> = api.teamRankings(city)
    suspend fun playerRankings(city: String): List<PlayerRanking> = api.playerRankings(city)
}
