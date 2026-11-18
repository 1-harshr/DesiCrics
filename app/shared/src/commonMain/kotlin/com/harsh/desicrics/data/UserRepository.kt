package com.harsh.desicrics.data

import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class UserRepository(private val api: DesiCricsApi) {

    private val savedProfiles = MutableSharedFlow<UserProfile>(extraBufferCapacity = 1)

    /** The signed-in user's profile: fetched once, then updated whenever it's saved from this device. */
    fun observeProfile(uid: String): Flow<UserProfile?> = flow {
        emit(getProfile())
        emitAll(savedProfiles.filter { it.uid == uid })
    }

    suspend fun getProfile(): UserProfile? = api.myProfile()

    /** The server keys the profile by the signed-in user, so no UID is sent. */
    suspend fun saveProfile(request: UpdateProfileRequest): UserProfile =
        api.updateProfile(request).also { savedProfiles.emit(it) }

    /** Replaces the profile photo with [jpeg]; the new profile flows to everyone watching it. */
    suspend fun uploadPhoto(jpeg: ByteArray): UserProfile = api.uploadProfilePhoto(jpeg).also { savedProfiles.emit(it) }
}
