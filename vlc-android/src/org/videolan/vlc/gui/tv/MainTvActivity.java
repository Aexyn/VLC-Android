package org.videolan.vlc.gui.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.Thumbnailer;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.video.VideoBrowserInterface;
import org.videolan.vlc.gui.video.VideoListHandler;
import org.videolan.vlc.util.Util;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.Row;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

public class MainTvActivity extends Activity implements VideoBrowserInterface {

	private static final int NUM_ITEMS_PREVIEW = 5;

	public static final String TAG = "BrowseActivity";

	protected BrowseFragment mBrowseFragment;
	protected final CyclicBarrier mBarrier = new CyclicBarrier(2);
	private MediaLibrary mMediaLibrary;
	private Thumbnailer mThumbnailer;
	private Media mItemToUpdate;
	ArrayObjectAdapter mRowsAdapter;
	ArrayObjectAdapter videoAdapter;
	ArrayObjectAdapter audioAdapter;
	HashMap<String, Integer> mVideoIndex;
	Drawable mDefaultBackground;
	Activity mContext;

	OnItemClickedListener mItemClickListener = new OnItemClickedListener() {
		@Override
		public void onItemClicked(Object item, Row row) {
			TvUtil.openMedia(mContext, (Media)item, row);
		}
	};

	OnClickListener mSearchClickedListenernew = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(mContext, SearchActivity.class);
            startActivity(intent);
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/*
		 * skip browser and show direcly Audio Player if a song is playing
		 */
		if (LibVLC.getExistingInstance() != null){
			if (LibVLC.getExistingInstance().isPlaying()){
				startActivity(new Intent(this, AudioPlayerActivity.class));
				finish();
				return;
			}
		}
		mContext = this;
		setContentView(R.layout.tv_main_fragment);

		mMediaLibrary = MediaLibrary.getInstance();
		mDefaultBackground = getResources().getDrawable(R.drawable.background);
		final FragmentManager fragmentManager = getFragmentManager();
		mBrowseFragment = (BrowseFragment) fragmentManager.findFragmentById(
				R.id.browse_fragment);

		// Set display parameters for the BrowseFragment
		mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
		mBrowseFragment.setTitle(getString(R.string.app_name));
		mBrowseFragment.setBadgeDrawable(getResources().getDrawable(R.drawable.cone));
        // set search icon color
		mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.darkorange));

		// add a listener for selected items
		mBrowseFragment.setOnItemClickedListener(mItemClickListener);

		mBrowseFragment.setOnSearchClickedListener(mSearchClickedListenernew);
		mMediaLibrary.loadMediaItems(this, true);
		mThumbnailer = new Thumbnailer(this, getWindowManager().getDefaultDisplay());
		BackgroundManager.getInstance(this).attach(getWindow());
	}

	public void onResume() {
		super.onResume();
		mMediaLibrary.addUpdateHandler(mHandler);
		if (mMediaLibrary.isWorking()) {
			Util.actionScanStart();
		}

		/* Start the thumbnailer */
		if (mThumbnailer != null)
			mThumbnailer.start(this);
	}

	public void onPause() {
		super.onPause();
		mMediaLibrary.removeUpdateHandler(mHandler);

		/* Stop the thumbnailer */
		if (mThumbnailer != null)
			mThumbnailer.stop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mThumbnailer != null)
			mThumbnailer.clearJobs();
		mBarrier.reset();
	}

    protected void updateBackground(Drawable drawable) {
        BackgroundManager.getInstance(this).setDrawable(drawable);
    }

    protected void clearBackground() {
        BackgroundManager.getInstance(this).setDrawable(mDefaultBackground);
    }

	public void await() throws InterruptedException, BrokenBarrierException {
		mBarrier.await();
	}

	public void resetBarrier() {
		mBarrier.reset();
	}

	public void updateList() {
		new AsyncUpdate().execute();
	}

	@Override
	public void setItemToUpdate(Media item) {
		mItemToUpdate = item;
		mHandler.sendEmptyMessage(VideoListHandler.UPDATE_ITEM);
	}

	public void updateItem() {
		videoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(mItemToUpdate.getLocation()), 1);
		try {
			mBarrier.await();
		} catch (InterruptedException e) {
		} catch (BrokenBarrierException e) {}
	}

	private Handler mHandler = new VideoListHandler(this);

	public class AsyncUpdate extends AsyncTask<Void, Void, Void> {

		public AsyncUpdate() { }

		@Override
		protected void onPreExecute(){
			mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
		}
		@Override
		protected Void doInBackground(Void... params) {
			MediaDatabase mediaDatabase = MediaDatabase.getInstance();
			ArrayList<Media> videoList = mMediaLibrary.getVideoItems();
			ArrayList<Media> audioList = mMediaLibrary.getAudioItems();
			int size;
			Media item;
			Bitmap picture;

			// Update video section
			if (!videoList.isEmpty()) {
				size = videoList.size();
				mVideoIndex = new HashMap<String, Integer>(size);
				videoAdapter = new ArrayObjectAdapter(
						new CardPresenter());
				if (NUM_ITEMS_PREVIEW < size)
					size = NUM_ITEMS_PREVIEW;
				for (int i = 0 ; i < size ; ++i) {
					item = videoList.get(i);
					picture = mediaDatabase.getPicture(mContext, item.getLocation());

					videoAdapter.add(item);
					mVideoIndex.put(item.getLocation(), i);
					if (mThumbnailer != null){
						if (picture== null) {
							mThumbnailer.addJob(item);
						} else {
							MediaDatabase.setPicture(item, picture);
							picture = null;
						}
					}
				}
				// Empty item to launch grid activity
				videoAdapter.add(new Media(null, 0, 0, Media.TYPE_GROUP, null, "Browse more", null, null, null, 0, 0, null, 0, 0));

				HeaderItem header = new HeaderItem(HEADER_VIDEO, getString(R.string.video), null);
				mRowsAdapter.add(new ListRow(header, videoAdapter));
			}

			// update audio section
			if (!audioList.isEmpty()) {
				size = audioList.size();
				if (NUM_ITEMS_PREVIEW < size)
					size = NUM_ITEMS_PREVIEW;
				audioAdapter = new ArrayObjectAdapter(new CardPresenter());
				for (int i = 0 ; i < size ; ++i) {
					item = audioList.get(i);
					picture = AudioUtil.getCover(mContext, item, 320);
					if (picture != null){
						MediaDatabase.setPicture(item, picture);
						picture = null;
					}
					audioAdapter.add(item);

				}
				// Empty item to launch grid activity
				audioAdapter.add(new Media(null, 0, 0, Media.TYPE_GROUP, null, "Browse more", null, null, null, 0, 0, null, 0, 0));

				HeaderItem header = new HeaderItem(HEADER_MUSIC, getString(R.string.audio), null);
				mRowsAdapter.add(new ListRow(header, audioAdapter));
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			mBrowseFragment.setAdapter(mRowsAdapter);
		}
	}
}
