package pk.qwerty12.nfclockscreenoffenabler;

import java.util.ArrayList;
import java.util.HashSet;

import pk.qwerty12.nfclockscreenoffenabler.UndoBarController.UndoListener;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.haarman.listviewanimations.itemmanipulation.OnDismissCallback;
import com.haarman.listviewanimations.itemmanipulation.SwipeDismissAdapter;

public class NfcTags extends Activity implements UndoListener {

	private SharedPreferences mPrefs = null;
	private NfcAdapter mNfcAdapter = null;
	private PendingIntent mPendingIntent = null;
	private IntentFilter[] mIntentFiltersArray = null;
	private String[][] mTechListsArray = null;
	private boolean mAlreadySetup = false;
	private ListView mListView = null;
	private NfcTagArrayAdapter mArrayAdapter;
	private boolean mDialogShowing = false;
	private UndoBarController mUndoBarController;

	@SuppressLint("WorldReadableFiles")
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nfc_tags);

		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		mPrefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);

		mListView = (ListView) findViewById(R.id.listView);
		mUndoBarController = new UndoBarController(findViewById(R.id.undobar), this);
		setupListViewFromSet();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		Editor editor = mPrefs.edit();

		editor.putStringSet(Common.PREF_NFC_KEYS, mArrayAdapter.getTagIds());
		editor.putStringSet(Common.PREF_NFC_KEYS_NAMES, mArrayAdapter.getTagNames());
		editor.commit();

		Intent i = new Intent(Common.SETTINGS_UPDATED_INTENT);
		sendBroadcast(i);
		super.onBackPressed();
	}

	private void setupListViewFromSet() {
		HashSet<String> defaultValue = new HashSet<String>();
		String[] nfcTagIds = mPrefs.getStringSet(Common.PREF_NFC_KEYS, defaultValue).toArray(new String[0]);
		String[] nfcTagNames = mPrefs.getStringSet(Common.PREF_NFC_KEYS_NAMES, defaultValue).toArray(new String[0]);

		ArrayList<NfcTag> nfcTagsArray = new ArrayList<NfcTag>();

		for (int i = 0; i < nfcTagIds.length; i++) {
			String tagId = nfcTagIds[i];
			String tagName;
			try {
				tagName = nfcTagNames[i];
			} catch (Exception e) {
				tagName = getString(R.string.unnamed_tag);
			}

			nfcTagsArray.add(new NfcTag(tagId, tagName));
		}

		mArrayAdapter = new NfcTagArrayAdapter(this, R.layout.nfc_tag_row, nfcTagsArray);
		SwipeDismissAdapter swipeAdapter = new SwipeDismissAdapter(mArrayAdapter, new OnDismissCallback() {

			@Override
			public void onDismiss(AbsListView listView, int[] reverseSortedPositions) {
				for (int position : reverseSortedPositions) {
					NfcTag tag = mArrayAdapter.getItem(position);
					mArrayAdapter.remove(position);
					mUndoBarController.showUndoBar(false, R.string.tag_unauthorized, tag);
				}
			}
		});

		swipeAdapter.setAbsListView(mListView);
		mListView.setAdapter(swipeAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.nfc_tags, menu);
		return true;
	}

	@Override
	protected void onPause() {
		if (mNfcAdapter == null)
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mNfcAdapter != null) {
			mNfcAdapter.disableForegroundDispatch(this);
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mNfcAdapter == null)
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

		if (mNfcAdapter != null) {
			setUpForegroundDispatchSystem();
			mNfcAdapter.enableForegroundDispatch(this, mPendingIntent,
					mIntentFiltersArray, mTechListsArray);
		}
	}

	private void setUpForegroundDispatchSystem() {
		if (mAlreadySetup)
			return;
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		
		mIntentFiltersArray = new IntentFilter[] {tech};
		mTechListsArray = new String[][] {
				new String[] { NfcA.class.getName() },
				new String[] { NfcB.class.getName() },
				new String[] { NfcF.class.getName() },
				new String[] { NfcV.class.getName() },
				new String[] { IsoDep.class.getName() },
				new String[] { MifareClassic.class.getName() },
				new String[] { MifareUltralight.class.getName() },
				new String[] { NdefFormatable.class.getName() },
				new String[] { Ndef.class.getName() } 
		};

		mAlreadySetup = true;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (mDialogShowing)
			return;

		Tag t = (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		String uuid = Common.byteArrayToHexString(t.getId());

		if (mArrayAdapter.containsTagId(uuid)) {
			Toast.makeText(this, R.string.tag_already_added, Toast.LENGTH_SHORT).show();
			return;
		}

		AlertDialog dialog = createAskForNameDialog(uuid);
		dialog.show();
		dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mDialogShowing = true;
	}

	private AlertDialog createAskForNameDialog(final String uuid) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.new_tag_detected);
		builder.setMessage(R.string.type_in_name_for_tag);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.setMargins(20, 0, 30, 0);

		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		input.requestFocus();
		layout.addView(input);
		builder.setView(layout);

		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int whichButton) {
				String name = input.getText().toString();
				if (TextUtils.isEmpty(name))
					name = getString(R.string.unnamed_tag);
				mArrayAdapter.add(new NfcTag(uuid, name));
				mDialogShowing = false;
				dialog.dismiss();
				return;
			}
		});

		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mDialogShowing = false;
				dialog.dismiss();
				return;
			}
		});

		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mDialogShowing = false;
				dialog.dismiss();
			}
		});

		final AlertDialog dialog = builder.create();
		input.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
					dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
				}
				return false;
			}
		});
		return dialog;
	}

	@Override
	public void onUndo(Parcelable token) {
		NfcTag tag = (NfcTag) token;
		mArrayAdapter.add(tag);
		mArrayAdapter.notifyDataSetChanged();
	}
}
