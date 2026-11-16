package ml.docilealligator.infinityforreddit.user;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;

import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.thing.SortType;
import retrofit2.Retrofit;

public class UserListingDataSourceFactory extends DataSource.Factory {
    private final Executor executor;
    private final Handler handler;
    private final Retrofit retrofit;
    @Nullable
    private final Retrofit oauthRetrofit;
    private final String query;
    private SortType sortType;
    @Nullable
    private final String accessToken;
    @NonNull
    private final String accountName;
    private final boolean nsfw;

    private UserListingDataSource userListingDataSource;
    private final MutableLiveData<UserListingDataSource> userListingDataSourceMutableLiveData;

    UserListingDataSourceFactory(Executor executor, Handler handler, Retrofit retrofit, @Nullable Retrofit oauthRetrofit,
                                 String query, SortType sortType,
                                 @Nullable String accessToken, @NonNull String accountName, boolean nsfw) {
        this.executor = executor;
        this.handler = handler;
        this.retrofit = retrofit;
        this.oauthRetrofit = oauthRetrofit;
        this.query = query;
        this.sortType = sortType;
        this.accessToken = accessToken;
        this.accountName = accountName;
        this.nsfw = nsfw;
        userListingDataSourceMutableLiveData = new MutableLiveData<>();
    }

    @NonNull
    @Override
    public DataSource create() {
        userListingDataSource = new UserListingDataSource(executor, handler, retrofit, oauthRetrofit,
                query, sortType, accessToken, accountName, nsfw);
        userListingDataSourceMutableLiveData.postValue(userListingDataSource);
        return userListingDataSource;
    }

    public MutableLiveData<UserListingDataSource> getUserListingDataSourceMutableLiveData() {
        return userListingDataSourceMutableLiveData;
    }

    UserListingDataSource getUserListingDataSource() {
        return userListingDataSource;
    }

    void changeSortType(SortType sortType) {
        this.sortType = sortType;
    }
}
