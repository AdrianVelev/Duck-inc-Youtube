package com.devteam.youtubemusic.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import com.devteam.youtubemusic.BackgroundExoAudioService;
import com.devteam.youtubemusic.R;
import com.devteam.youtubemusic.adapters.VideosAdapter;
import com.devteam.youtubemusic.database.YouTubeSqlDb;
import com.devteam.youtubemusic.model.YouTubeVideo;
import com.devteam.youtubemusic.ui.decoration.DividerDecoration;
import com.devteam.youtubemusic.utils.Config;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static com.devteam.youtubemusic.utils.Config.CUSTOM_ACTION_PLAY;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017-Mar-21
 */
public class FavoritesFragment extends RecyclerFragment
{
    private static final String TAG = FavoritesFragment.class.getSimpleName();

    private List<YouTubeVideo> favoriteVideos;
    private AdView mAdView;

    public static FavoritesFragment newInstance()
    {
        FavoritesFragment fragment = new FavoritesFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public FavoritesFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        favoriteVideos = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_recently_watched, container, false);

        MobileAds.initialize(getContext(), new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });
        mAdView = rootView.findViewById(R.id.adView);



        recyclerView = (RecyclerView) rootView.findViewById(R.id.recently_played);
        recyclerView.setLayoutManager(getLayoutManager());
        recyclerView.addItemDecoration(getItemDecoration());

        recyclerView.getItemAnimator().setAddDuration(500);
        recyclerView.getItemAnimator().setChangeDuration(500);
        recyclerView.getItemAnimator().setMoveDuration(500);
        recyclerView.getItemAnimator().setRemoveDuration(500);

        videoListAdapter = getAdapter();
        videoListAdapter.setOnItemClickListener(this);
        recyclerView.setAdapter(videoListAdapter);

        return rootView;
    }

    @Override
    public void onStart() {
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        super.onStart();
    }

    @Override
    protected RecyclerView.LayoutManager getLayoutManager()
    {
        return new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false);
    }

    @Override
    protected RecyclerView.ItemDecoration getItemDecoration()
    {
        //We must draw dividers ourselves if we want them in a list
        return new DividerDecoration(getActivity());
    }

    @Override
    protected VideosAdapter getAdapter()
    {
        return new VideosAdapter(getActivity(), true);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!getUserVisibleHint()) {
            //do nothing for now
        }
        favoriteVideos.clear();
        favoriteVideos.addAll(
                YouTubeSqlDb
                        .getInstance()
                        .videos(YouTubeSqlDb.VIDEOS_TYPE.FAVORITE)
                        .readAll()
        );

        if (videoListAdapter != null) {
            getActivity().runOnUiThread(() -> videoListAdapter.setYouTubeVideos(favoriteVideos));
        }
    }

    @Override
    public void setUserVisibleHint(boolean visible)
    {
        super.setUserVisibleHint(visible);

        if (visible && isResumed()) {
//            LogHelper.d(TAG, "RecentlyWatchedFragment visible and resumed");
            // Only manually call onResume if fragment is already visible
            // Otherwise allow natural fragment lifecycle to call onResume
            onResume();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        // Check network connectivity
        if (!networkConf.isNetworkAvailable(getActivity())) {
            networkConf.createNetErrorDialog();
            return;
        }

        Toast.makeText(
                getContext(),
                getResources().getString(R.string.toast_message_loading),
                Toast.LENGTH_SHORT
        ).show();

        favoriteVideos.clear();
        favoriteVideos.addAll(
                YouTubeSqlDb
                        .getInstance()
                        .videos(YouTubeSqlDb.VIDEOS_TYPE.FAVORITE)
                        .readAll()
        );

        // Adds items in the recently watched list
        YouTubeSqlDb
                .getInstance()
                .videos(YouTubeSqlDb.VIDEOS_TYPE.RECENTLY_WATCHED)
                .create(videoListAdapter.getYouTubeVideo(position));

        Intent serviceIntent = new Intent(getContext(), BackgroundExoAudioService.class);
        serviceIntent.setAction(CUSTOM_ACTION_PLAY);
        serviceIntent.putExtra(Config.KEY_YOUTUBE_TYPE, Config.YOUTUBE_MEDIA_TYPE_PLAYLIST);
        serviceIntent.putExtra(Config.KEY_YOUTUBE_TYPE_PLAYLIST, (ArrayList)favoriteVideos);
        serviceIntent.putExtra(Config.KEY_YOUTUBE_TYPE_PLAYLIST_VIDEO_POS, position);
        getActivity().startService(serviceIntent);
    }

    /**
     * Clears FavoriteList played list items
     */
    public void clearFavoritesList()
    {
        favoriteVideos.clear();
        videoListAdapter.notifyDataSetChanged();
    }
}
