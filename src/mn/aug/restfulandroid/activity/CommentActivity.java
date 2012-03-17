package mn.aug.restfulandroid.activity;

import java.io.File;

import mn.aug.restfulandroid.R;
import mn.aug.restfulandroid.RestfulAndroid;
import mn.aug.restfulandroid.provider.CatPicturesProviderContract.CatPicturesTable;
import mn.aug.restfulandroid.provider.CatPicturesProviderContract.CommentsTable;
import mn.aug.restfulandroid.rest.resource.CatPictures;
import mn.aug.restfulandroid.rest.resource.Comments;
import mn.aug.restfulandroid.service.CatPicturesServiceHelper;
import mn.aug.restfulandroid.util.Logger;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class CommentActivity extends Activity {

	public static final String EXTRA_CAT_PICTURE_ID = "catPicId";
	private static final String TAG = CatPicturesActivity.class.getSimpleName();

	private Long requestId;
	/**
	 * Used to send and retrieve data from the underlying api. 
	 */
	private CatPicturesServiceHelper mCatPicturesServiceHelper;
	/**
	 * Receives callbacks from the service helper
	 */
	private BroadcastReceiver requestReceiver;
	private IntentFilter filter = new IntentFilter(CatPicturesServiceHelper.ACTION_REQUEST_RESULT);

	private RestfulAndroid app;
	
	/**
	 * local database key
	 */
	private String catPictureId;

	private long mPostCommentRequestId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// ALLOW PROGRESS SPINNER IN TITLE BAR
		// THIS MUST COME BEFORE setContentView()!
		this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setProgressBarIndeterminateVisibility(false);

		setContentView(R.layout.comments);
		app = (RestfulAndroid)getApplicationContext();

		catPictureId = getIntent().getStringExtra(EXTRA_CAT_PICTURE_ID);

		this.requestReceiver = new CatPicturesReceiver();
		
		initPostFields();
		initComments();
		
		// allow posting of new comments
		Button postButton = (Button) findViewById(R.id.button_post_comment);
		postButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				postComment();				
			}
		});

	}

	private void initPostFields(){

		String whereClause = CatPicturesTable._ID + "=?";
		String[] whereValues = {this.catPictureId};
		Cursor cursor = getContentResolver().query(CatPictures.CONTENT_URI, CatPicturesTable.DISPLAY_COLUMNS, whereClause, whereValues, null);
		if(cursor.moveToFirst()){
			String title = cursor.getString(cursor.getColumnIndex(CatPicturesTable.TITLE));
			String thumbnailUrl = cursor.getString(cursor.getColumnIndex(CatPicturesTable.THUMBNAIL_URL));				
			Uri thumbnailUri = Uri.parse(thumbnailUrl);
			String filename = thumbnailUri.getLastPathSegment();
			File thumbFile = new File(app.getThumbnailsDir(), filename);

			TextView titleView = (TextView)findViewById(R.id.post_title);
			ImageView thumbView = (ImageView)findViewById(R.id.cat_picture);

			titleView.setText(title);
			thumbView.setImageURI(Uri.parse(thumbFile.getPath()));
			
		}
		
		cursor.close();
	}

	private void initComments(){

		
		// GET EXISTING COMMENTS TO INIT LIST
		String whereClause = CommentsTable.CAT_PICTURE_ID + "=?";
		String[] whereValues = {this.catPictureId};

		Cursor cursor = getContentResolver().query(Comments.CONTENT_URI,
				CommentsTable.DISPLAY_COLUMNS, whereClause, whereValues,
				CommentsTable.CREATED + " DESC");

		startManagingCursor(cursor);

		// CREATE THE ADAPTER USING THE CURSOR
		// THIS BINDS THE DATA IN THE CURSOR TO THE VIEW FOR EACH ROW IN THE LIST

		CommentsCursorAdapter mAdapter = new CommentsCursorAdapter(this, cursor);

		// SET THIS ADAPTER AS YOUR LISTACTIVITY'S ADAPTER
		ListView listView = (ListView)findViewById(R.id.comments_list);
		listView.setAdapter(mAdapter);
	}

	@Override
	protected void onResume() {
		super.onResume();

		/*
		 * 1. Register for callbacks broadcast from the CatPicturesServiceHelper
		 */
		this.registerReceiver(this.requestReceiver, this.filter);

		/*
		 * 2. See if we've already made a request. 
		 * a. If not, make the request
		 * b. If so, check if it is still in progress or was completed while we were paused
		 */
		mCatPicturesServiceHelper = new CatPicturesServiceHelper(this);

		if (requestId == null) {
			requestId = mCatPicturesServiceHelper.getComments(catPictureId);
			// show progress spinner
			setProgressBarIndeterminateVisibility(true);
		} else if (mCatPicturesServiceHelper.isRequestPending(requestId)) {
			// show progress spinner
			setProgressBarIndeterminateVisibility(true);
		} else {
			// stop progress spinner, request already received, data updated
			setProgressBarIndeterminateVisibility(false);
		}

	}

	@Override
	protected void onPause() {
		super.onPause();

		// Unregister for broadcast
		try {
			this.unregisterReceiver(requestReceiver);
		} catch (IllegalArgumentException e) {
			Logger.warn(TAG, "Likely receiver wasn't registered, ok to ignore");
		}
	}
	
	private void postComment(){
		EditText commentField = (EditText) findViewById(R.id.new_comment_entry);
		Editable comment = commentField.getText();
		mCatPicturesServiceHelper.submitNewComment(this.catPictureId, comment.toString());
		commentField.setText(null);
	}

	class CatPicturesReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {

			// Returns the id of original request
			long resultRequestId = intent.getLongExtra(
					CatPicturesServiceHelper.EXTRA_REQUEST_ID, 0);

			Logger.debug(TAG, "Received intent " + intent.getAction() + ", request ID "
					+ resultRequestId);

			// check if this was OUR request
			if (resultRequestId == requestId) {

				// This was our request, stop the progress spinner
				CommentActivity.this.setProgressBarIndeterminateVisibility(false);
				Logger.debug(TAG, "Result is for our request ID");

				// What was the result of our request?
				int resultCode = intent.getIntExtra(CatPicturesServiceHelper.EXTRA_RESULT_CODE, 0);

				Logger.debug(TAG, "Result code = " + resultCode);

				// HERE WE COULD GIVE SOME FEEDBACK TO USER INDICATING IF DATA
				// HAS BEEN UPDATED
				// OR IF AN ERROR HAS OCCURED
				if (resultCode == 200) {
					Logger.info(TAG, "Request Succeeded");
				} else {
					Logger.warn(TAG, "Error executing request:" + resultCode);
				}
			} else if (resultRequestId == mPostCommentRequestId) {
				Logger.debug(TAG, "Result is for our post comment request ID");
				
				// This was our post comment request, stop the progress spinner

				// What was the result of our request?
				int resultCode = intent.getIntExtra(CatPicturesServiceHelper.EXTRA_RESULT_CODE, 0);

				Logger.debug(TAG, "Result code = " + resultCode);

				// HERE WE COULD GIVE SOME FEEDBACK TO USER INDICATING IF DATA
				// HAS BEEN UPDATED
				// OR IF AN ERROR HAS OCCURED
				if (resultCode == 200) {
					Logger.info(TAG, "Request Succeeded");
				} else {
					Logger.warn(TAG, "Error executing request:" + resultCode);
					Toast.makeText(CommentActivity.this, "Error posting comment", Toast.LENGTH_LONG).show();
				}
			} else {
				// IGNORE, wasn't for our request
				Logger.debug(TAG, "Result is NOT for our request ID");
			}

		}
	}

}