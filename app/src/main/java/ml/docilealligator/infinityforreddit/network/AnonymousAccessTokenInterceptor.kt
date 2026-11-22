package ml.docilealligator.infinityforreddit.network

import ml.docilealligator.infinityforreddit.database.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.utils.APIUtils
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.concurrent.atomic.AtomicReference

class AnonymousAccessTokenInterceptor(
    private val retrofit: Retrofit, private val redditDataRoomDatabase: RedditDataRoomDatabase
) : Interceptor {
    private val cachedAccessToken = AtomicReference<String?>(null)
    private val refreshLock = Any()
    @Volatile
    private var tokenVersion: Int = APIUtils.getAnonymousTokenVersion()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        if (path.contains("/api/v1/access_token")
            || path.contains("/api/v1/authorize")
            || !originalRequest.url.host.equals("www.reddit.com")) {
            return chain.proceed(originalRequest)
        }

        val newUrl = originalRequest.url.newBuilder().host("oauth.reddit.com").build()

        syncTokenVersionIfNeeded()

        var token = getOrFetchToken()
        if (token.isNullOrBlank()) {
            synchronized(refreshLock) {
                syncTokenVersionLocked()
                val currentAccessToken = getOrFetchToken()
                token = if (!currentAccessToken.isNullOrBlank()) {
                    currentAccessToken
                } else {
                    refreshToken()
                }
            }
        }

        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .url(newUrl)
            .header("Authorization", "bearer $token")
            .build()

        val response = chain.proceed(authenticatedRequest)
        if (response.code == 401 || response.code == 403) {
            response.close()

            val expiredAccessToken = response.request.header("Authorization")?.replace("bearer ", "")

            synchronized(refreshLock) {
                syncTokenVersionLocked()
                val currentAccessToken = getOrFetchToken()

                val newAccessToken = if (currentAccessToken != expiredAccessToken && !currentAccessToken.isNullOrBlank()) {
                    currentAccessToken
                } else {
                    try {
                        refreshToken()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return chain.proceed(originalRequest)
                    }
                }

                return newAccessToken?.let {
                    chain.proceed(originalRequest.newBuilder()
                        .url(newUrl)
                        .header("Authorization", "Bearer $newAccessToken")
                        .build())
                } ?: chain.proceed(response.request.newBuilder().build())
            }
        } else {
            return response
        }
    }

    private fun syncTokenVersionIfNeeded() {
        if (tokenVersion != APIUtils.getAnonymousTokenVersion()) {
            synchronized(refreshLock) {
                syncTokenVersionLocked()
            }
        }
    }

    private fun syncTokenVersionLocked() {
        val latestVersion = APIUtils.getAnonymousTokenVersion()
        if (tokenVersion != latestVersion) {
            cachedAccessToken.set(null)
            redditDataRoomDatabase.accountDaoKt().setAnonymousAccessToken(null)
            tokenVersion = latestVersion
        }
    }

    private fun getOrFetchToken(): String? {
        val cached = cachedAccessToken.get()
        if (!cached.isNullOrBlank()) {
            return cached
        }
        return synchronized(refreshLock) {
            val currentCached = cachedAccessToken.get()
            if (!currentCached.isNullOrBlank()) {
                currentCached
            } else {
                val accessToken = redditDataRoomDatabase.accountDaoKt().getAnonymousAccessToken()
                if (!accessToken.isNullOrBlank()) {
                    cachedAccessToken.set(accessToken)
                    accessToken
                } else {
                    null
                }
            }
        }
    }

    private fun refreshToken(): String? {
        return try {
            val params = mutableMapOf<String, String>()
            params[APIUtils.GRANT_TYPE_KEY] = "https://oauth.reddit.com/grants/installed_client"
            params["device_id"] = "DO_NOT_TRACK_THIS_DEVICE"

            val response = retrofit.create(RedditAPIKt::class.java).getAnonymousAccessToken(
                APIUtils.getHttpBasicAuthHeader(), params
            ).execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body.isNullOrBlank()) return null
                val newAccessToken = JSONObject(body).getString(APIUtils.ACCESS_TOKEN_KEY)
                redditDataRoomDatabase.accountDaoKt().setAnonymousAccessToken(newAccessToken)
                cachedAccessToken.set(newAccessToken)

                newAccessToken
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}