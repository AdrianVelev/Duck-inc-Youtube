package com.devteam.youtubemusic.youtube;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.devteam.youtubemusic.interfaces.YouTubeVideoReceiver;
import com.devteam.youtubemusic.model.YouTubeVideo;
import com.devteam.youtubemusic.utils.Config;
import com.devteam.youtubemusic.utils.LogHelper;
import com.devteam.youtubemusic.utils.StringUtils;
import com.devteam.youtubemusic.utils.Utils;

import java.io.IOException;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.devteam.youtubemusic.utils.Config.YOUTUBE_LANGUAGE_KEY;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_SEARCH_LIST_FIELDS;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_SEARCH_LIST_PART;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_SEARCH_LIST_TYPE;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_VIDEO_FIELDS;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_VIDEO_LIST_FIELDS;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_VIDEO_LIST_PART;
import static com.devteam.youtubemusic.utils.Config.YOUTUBE_VIDEO_PART;
import static com.devteam.youtubemusic.utils.Config.YT_REGEX;
import static com.devteam.youtubemusic.youtube.YouTubeSingleton.getInstance;
import static com.devteam.youtubemusic.youtube.YouTubeSingleton.getYouTube;



public class YouTubeVideoLoader extends AsyncTask<String, Void, List<YouTubeVideo>>
{
    private static final String TAG = LogHelper.makeLogTag(YouTubeVideoLoader.class);

    private Context context;
    private YouTube youtube;

    private YouTube.Search.List searchList;

    private String keywords;
    private String currentPageToken;
    private String nextPageToken;

    private YouTubeVideoReceiver youTubeVideoReceiver;

    private String language;

    public YouTubeVideoLoader(Context context)
    {
        getInstance(context);
        this.context = context;
        this.youtube = getYouTube();
        this.keywords = null;
        this.currentPageToken = null;
        this.nextPageToken = null;
        this.youTubeVideoReceiver = null;
        this.language = Locale.getDefault().getLanguage();
    }

    @Override
    protected List<YouTubeVideo> doInBackground(String... params)
    {
        if (keywords == null) return null;
        try {
            return searchVideos();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(List<YouTubeVideo> ytVideos)
    {
        youTubeVideoReceiver.onVideosReceived(ytVideos, currentPageToken, nextPageToken);
    }

    /**
     * Start the search.
     *
     * @param keywords - query
     */
    public void search(String keywords)
    {
        this.keywords = keywords;
        this.currentPageToken = null;
        this.nextPageToken = null;
        this.execute();
    }

    /**
     * Start the search.
     *
     * @param keywords         - query
     * @param currentPageToken - contains the Page Token
     */
    public void search(String keywords, String currentPageToken)
    {
        this.keywords = keywords;
        this.currentPageToken = currentPageToken;
        this.nextPageToken = null;
//        this.execute();
        this.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    public void setYouTubeVideoReceiver(YouTubeVideoReceiver youTubeVideoReceiver)
    {
        this.youTubeVideoReceiver = youTubeVideoReceiver;
    }

    /**
     * Search videos for a specific query
     */
    private List<YouTubeVideo> searchVideos()
    {
        List<YouTubeVideo> ytVideos = new ArrayList<>();
        try {
            searchList = youtube.search().list(YOUTUBE_SEARCH_LIST_PART);

            searchList.setQ(keywords);
            searchList.setKey(Config.YOUTUBE_API_KEY);
            searchList.setType(YOUTUBE_SEARCH_LIST_TYPE); //TODO ADD PLAYLISTS SEARCH
            searchList.setMaxResults(Config.MAX_VIDEOS_RETURNED);
            searchList.setFields(YOUTUBE_SEARCH_LIST_FIELDS);
            searchList.set(YOUTUBE_LANGUAGE_KEY, language);
            if (currentPageToken != null) {
                searchList.setPageToken(currentPageToken);
            }


            final Pattern pattern = Pattern.compile(YT_REGEX);
            final Matcher matcher = pattern.matcher(keywords);

            if (matcher.find()) {
                Log.e(TAG, "YouTube ID: " + matcher.group(1));

                YouTube.Videos.List singleVideo = youtube.videos().list(YOUTUBE_VIDEO_PART);
                singleVideo.setKey(Config.YOUTUBE_API_KEY);
                singleVideo.setFields(YOUTUBE_VIDEO_FIELDS);
                singleVideo.set(YOUTUBE_LANGUAGE_KEY, language);
                singleVideo.setId(matcher.group(1));
                VideoListResponse resp = singleVideo.execute();
                List<Video> videoResults = resp.getItems();

                for (Video videoResult : videoResults) {
                    YouTubeVideo item = new YouTubeVideo();

                    if (videoResult != null) {
                        // SearchList list info
                        item.setTitle(videoResult.getSnippet().getTitle());
                        item.setThumbnailURL(videoResult.getSnippet().getThumbnails().getDefault().getUrl());
                        item.setId(videoResult.getId());

                        // Video info
                        if (videoResult.getStatistics() != null) {
                            BigInteger viewsNumber = videoResult.getStatistics().getViewCount();
                            String viewsFormatted = NumberFormat.getIntegerInstance().format(viewsNumber) + " views";
                            item.setViewCount(viewsFormatted);
                        }
                        if (videoResult.getContentDetails() != null) {
                            String isoTime = videoResult.getContentDetails().getDuration();
                            String time = Utils.convertISO8601DurationToNormalTime(isoTime);
                            item.setDuration(time);
                        }
                    } else {
                        item.setDuration("NA");
                    }

                    // Add to the list
                    ytVideos.add(item);
                }
            } else {
                YouTube.Videos.List videosList = youtube.videos().list(YOUTUBE_VIDEO_LIST_PART);
                videosList.setKey(Config.YOUTUBE_API_KEY);
                videosList.setFields(YOUTUBE_VIDEO_LIST_FIELDS);
                videosList.set(YOUTUBE_LANGUAGE_KEY, language);

                // Search Response
                final SearchListResponse searchListResponse = searchList.execute();
                Log.e(TAG, "Printed " + searchListResponse.getPageInfo().getResultsPerPage() +
                        " out of " + searchListResponse.getPageInfo().getTotalResults() +
                        ".\nCurrent page token: " + searchList.getPageToken() + "\n" +
                        "Next page token: " + searchListResponse.getNextPageToken() +
                        ".\nPrev page token: " + searchListResponse.getPrevPageToken());
                final List<SearchResult> searchResults = searchListResponse.getItems();

                // Stores the nextPageToken
                nextPageToken = searchListResponse.getNextPageToken();

                // Finds video list
                videosList.setId(Utils.concatenateIDs(searchResults));
                VideoListResponse resp = videosList.execute();
                List<Video> videoResults = resp.getItems();

                // Create the ytVideos list to be displayed in the UI
                int index = 0;
                for (SearchResult result : searchResults) {
                    if (result.getId() == null) {
                        continue;
                    }

                    YouTubeVideo item = new YouTubeVideo();

                    String title = StringUtils.unescapeHtml3(result.getSnippet().getTitle());
                    LogHelper.e(TAG, "Title: " + title);
                    // SearchList list info
                    item.setTitle(title);
                    item.setThumbnailURL(result.getSnippet().getThumbnails().getDefault().getUrl());
                    item.setId(result.getId().getVideoId());

                    // Video info
                    Video videoResult = videoResults.get(index);
                    if (videoResult != null) {
                        if (videoResult.getStatistics() != null) {
                            BigInteger viewsNumber = videoResult.getStatistics().getViewCount();
                            String viewsFormatted = NumberFormat.getIntegerInstance().format(viewsNumber) + " views";
                            item.setViewCount(viewsFormatted);
                        }
                        if (videoResult.getContentDetails() != null) {
                            String isoTime = videoResult.getContentDetails().getDuration();
                            String time = Utils.convertISO8601DurationToNormalTime(isoTime);
                            item.setDuration(time);
                        }
                    } else {
                        item.setDuration("NA");
                    }

                    // Add to the list
                    ytVideos.add(item);
                    index++;

                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not initialize: " + e);
            e.printStackTrace();
        }

        Log.e(TAG, "LoadInBackground: return " + ytVideos.size());
        return ytVideos;
    }
}