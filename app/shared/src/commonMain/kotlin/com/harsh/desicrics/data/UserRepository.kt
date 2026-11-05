package com.harsh.desicrics.data

import com.harsh.desicrics.data.sqlconnect.GetMyProfileData
import com.harsh.desicrics.data.sqlconnect.IgnoredData
import com.harsh.desicrics.data.sqlconnect.NoVariables
import com.harsh.desicrics.data.sqlconnect.SqlConnectClient
import com.harsh.desicrics.data.sqlconnect.UpsertMyProfileVariables
import com.harsh.desicrics.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class UserRepository(private val sqlConnect: SqlConnectClient) {

    private val savedProfiles = MutableSharedFlow<UserProfile>(extraBufferCapacity = 1)

    /** The signed-in user's profile: fetched once, then updated whenever it's saved from this device. */
    fun observeProfile(uid: String): Flow<UserProfile?> = flow {
        emit(getProfile())
        emitAll(savedProfiles.filter { it.uid == uid })
    }

    suspend fun getProfile(): UserProfile? =
        sqlConnect.query<NoVariables, GetMyProfileData>("GetMyProfile", NoVariables).user?.toModel()

    /** The server keys the row by the caller's auth UID, so [UserProfile.uid] isn't sent. */
    suspend fun saveProfile(profile: UserProfile) {
        sqlConnect.mutation<UpsertMyProfileVariables, IgnoredData>(
            "UpsertMyProfile",
            UpsertMyProfileVariables(
                name = profile.name,
                phone = profile.phone,
                email = profile.email,
                photoUrl = profile.photoUrl,
                city = profile.city,
                role = profile.role,
                battingStyle = profile.battingStyle,
                bowlingStyle = profile.bowlingStyle,
            ),
        )
        savedProfiles.emit(profile)
    }
}
