package org.videolan.vlc.gui.tv;

import java.util.ArrayList;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.util.Log;

public class MediaItemDetailsFragment extends DetailsFragment implements AudioServiceController.AudioServiceConnectionListener {
	private static final String TAG = "MediaItemDetailsFragment";
	private static final int ID_PLAY = 1;
	private static final int ID_LISTEN = 2;
	private ArrayObjectAdapter mRowsAdapter;
    private AudioServiceController mAudioController;
    private AudioPlayer mAudioPlayer;
    private MediaItemDetails mMedia;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();
		buildDetails();
	}

	public void onResume(){
		super.onResume();
	}

	public void onPause(){
		super.onPause();
		if (mAudioController.isPlaying()){
			mAudioController.stop();
			mAudioController.unbindAudioService(getActivity());
		}
	}

	private void buildDetails() {
		Bundle extras = getActivity().getIntent().getExtras();
		mMedia = extras.getParcelable("item");
		ClassPresenterSelector selector = new ClassPresenterSelector();
		// Attach your media item details presenter to the row presenter:
		DetailsOverviewRowPresenter rowPresenter =
				new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

		rowPresenter.setBackgroundColor(getResources().getColor(R.color.darkorange));
		rowPresenter.setOnActionClickedListener(new OnActionClickedListener() {

			@Override
			public void onActionClicked(Action action) {
				if (action.getId() == ID_LISTEN){
					mAudioController.bindAudioService(getActivity(), MediaItemDetailsFragment.this);
				} else if (action.getId() == ID_PLAY){
					ArrayList<String> locations = new ArrayList<String>();
					locations.add(mMedia.getLocation());
					Intent intent = new Intent(getActivity(), AudioPlayerActivity.class);
					intent.putExtra("locations", locations);
					startActivity(intent);
				}
			}
		});
		selector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
		selector.addClassPresenter(ListRow.class,
				new ListRowPresenter());
		mRowsAdapter = new ArrayObjectAdapter(selector);

		Resources res = getActivity().getResources();
		DetailsOverviewRow detailsOverview = new DetailsOverviewRow(mMedia);

		// Add images and action buttons to the details view
		Bitmap cover = AudioUtil.getCover(getActivity(), MediaLibrary.getInstance().getMediaItem(mMedia.getLocation()), 480);
		if (cover == null)
			detailsOverview.setImageDrawable(res.getDrawable(R.drawable.cone));
		else
			detailsOverview.setImageBitmap(getActivity(), cover);
		detailsOverview.addAction(new Action(ID_PLAY, "Play"));
		detailsOverview.addAction(new Action(ID_LISTEN, "Listen"));
		mRowsAdapter.add(detailsOverview);

		// Add a Related items row
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(
				new StringPresenter());
		listRowAdapter.add("Media Item 1");
		listRowAdapter.add("Media Item 2");
		listRowAdapter.add("Media Item 3");
		HeaderItem header = new HeaderItem(0, "Related Items", null);
		mRowsAdapter.add(new ListRow(header, listRowAdapter));

		setAdapter(mRowsAdapter);
	}

	@Override
	public void onConnectionSuccess() {
        mAudioController.load(mMedia.getLocation(), true);
	}

	@Override
	public void onConnectionFailed() {}

}
