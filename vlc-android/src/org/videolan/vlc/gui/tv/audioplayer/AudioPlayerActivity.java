package org.videolan.vlc.gui.tv.audioplayer;

import java.util.ArrayList;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.tv.audioplayer.PlaylistAdapter.ViewHolder;
import org.videolan.vlc.interfaces.IAudioPlayer;
import org.videolan.vlc.util.AndroidDevices;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements AudioServiceController.AudioServiceConnectionListener, IAudioPlayer{
	public static final String TAG = "AudioPlayerActivity";

	private Activity mContext;
    private AudioServiceController mAudioController;
    private RecyclerView mRecyclerView;
    private Adapter<ViewHolder> mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private ArrayList<String> mLocations;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    private TextView mTitleTv, mArtistTv;
    private ImageView mPlayPauseButton, mCover;
    private ProgressBar mProgressBar;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tv_audio_player);

		mContext = this;
		mLocations = getIntent().getStringArrayListExtra("locations");
		mRecyclerView = (RecyclerView) findViewById(R.id.playlist);
		mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
		if (mLocations == null)
			mLocations = new ArrayList<String>();
		else {
			mAdapter = new PlaylistAdapter(mLocations);
        	mRecyclerView.setAdapter(mAdapter);
		}

		mAudioController = AudioServiceController.getInstance();

		mTitleTv = (TextView)findViewById(R.id.media_title);
		mArtistTv = (TextView)findViewById(R.id.media_artist);
		mPlayPauseButton = (ImageView)findViewById(R.id.button_play);
		mProgressBar = (ProgressBar)findViewById(R.id.media_progress);
		mCover = (ImageView)findViewById(R.id.album_cover);
	}

	public void onStart(){
		super.onStart();
		mAudioController.bindAudioService(this, this);
		mAudioController.addAudioPlayer(this);
	}

	public void onStop(){
		super.onStop();
		mAudioController.removeAudioPlayer(this);
		mAudioController.unbindAudioService(this);
		mLocations.clear();
	}

	@Override
	public void onConnectionSuccess() {
		ArrayList<String> medialocations = (ArrayList<String>) mAudioController.getMediaLocations();
		if (!mLocations.isEmpty() && !mLocations.equals(medialocations))
			mAudioController.load(mLocations, 0, true);
		else {
			mLocations = medialocations;
			update();
			mAdapter = new PlaylistAdapter(mLocations);
			mRecyclerView.setAdapter(mAdapter);
		}
	}

	@Override
	public void onConnectionFailed() {}

	@Override
	public void update() {
		mPlayPauseButton.setImageResource(mAudioController.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
		if (mAudioController.hasMedia()) {
			mTitleTv.setText(mAudioController.getTitle());
			mArtistTv.setText(mAudioController.getArtist());
			mProgressBar.setMax(mAudioController.getLength());
			Media media = MediaLibrary.getInstance().getMediaItem(mAudioController.getCurrentMediaLocation());
			Bitmap cover = AudioUtil.getCover(this, media, mCover.getWidth());
			if (cover == null)
				cover = mAudioController.getCover();
			if (cover == null)
				mCover.setImageResource(R.drawable.background_cone);
			else
				mCover.setImageBitmap(cover);
		}
	}

	@Override
	public void updateProgress() {
		mProgressBar.setProgress(mAudioController.getTime());
	}

	public boolean onKeyDown(int keyCode, KeyEvent event){
		switch (keyCode){
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        case KeyEvent.KEYCODE_MEDIA_PLAY:
        case KeyEvent.KEYCODE_MEDIA_PAUSE:
        case KeyEvent.KEYCODE_SPACE:
        case KeyEvent.KEYCODE_BUTTON_A:
			togglePlayPause();
			break;
        case KeyEvent.KEYCODE_F:
        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
        case KeyEvent.KEYCODE_BUTTON_R1:
			goNext();
			break;
        case KeyEvent.KEYCODE_R:
        case KeyEvent.KEYCODE_MEDIA_REWIND:
        case KeyEvent.KEYCODE_BUTTON_L1:
			goPrevious();
			break;
		default:
			return super.onKeyDown(keyCode, event);
		}
		return true;
	}

    @TargetApi(12) //only active for Android 3.1+
    public boolean dispatchGenericMotionEvent(MotionEvent event){

		InputDevice mInputDevice = event.getDevice();

		float x = AndroidDevices.getCenteredAxis(event, mInputDevice,
				MotionEvent.AXIS_X);
		float y = AndroidDevices.getCenteredAxis(event, mInputDevice,
				MotionEvent.AXIS_Y);
		float z = AndroidDevices.getCenteredAxis(event, mInputDevice,
				MotionEvent.AXIS_Z);
		float rz = AndroidDevices.getCenteredAxis(event, mInputDevice,
				MotionEvent.AXIS_RZ);

		if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY){
			if (Math.abs(x) > 0.3){
				seek(x > 0.0f ? 10000 : -10000);
				mLastMove = System.currentTimeMillis();
			} 
			//TODO Will we change volume in app on TV ?
			/*else if (Math.abs(rz) > 0.3){
				mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
				int delta = -(int) ((rz / 7) * mAudioMax);
				int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
				setAudioVolume(vol);
				mLastMove = System.currentTimeMillis();
			}*/
		}
		return true;
    }

	private void seek(int delta) {
		int time = mAudioController.getTime()+delta;
		if (time < 0 || time > mAudioController.getLength())
			return;
		mAudioController.setTime(time);
	}

	public void onClick(View v){
		switch (v.getId()){
		case R.id.button_play:
			togglePlayPause();
			break;
		case R.id.button_next:
			goNext();
			break;
		case R.id.button_previous:
			goPrevious();
			break;
		}
	}

	private void goPrevious() {
		if (mAudioController.hasPrevious()) {
			mAudioController.previous();
		}
	}

	private void goNext() {
		if (mAudioController.hasNext()){
			mAudioController.next();
		}
	}

	private void togglePlayPause() {
		if (mAudioController.isPlaying())
			mAudioController.pause();
		else if (mAudioController.hasMedia())
			mAudioController.play();
	}
}
