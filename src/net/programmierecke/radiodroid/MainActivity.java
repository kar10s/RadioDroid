package net.programmierecke.radiodroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class MainActivity extends ListActivity {
	private String itsAdressWWWTopClick25 = "http://www.radio-browser.info/webservice/json/stations/topclick/25";
	private String itsAdressWWWTopVote25 = "http://www.radio-browser.info/webservice/json/stations/topvote/25";

	ProgressDialog itsProgressLoading;
	RadioItemBigAdapter itsArrayAdapter = null;

	private static final String TAG = "RadioDroid";
	IPlayerService itsPlayerService;
	private ServiceConnection svcConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) {
			Log.v(TAG, "Service came online");
			itsPlayerService = IPlayerService.Stub.asInterface(binder);
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.v(TAG, "Service offline");
			itsPlayerService = null;
		}
	};

	private void RefillList(final String theURL) {
		itsProgressLoading = ProgressDialog.show(MainActivity.this, "", "Loading...");
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				return downloadFeed(theURL);
			}

			@Override
			protected void onPostExecute(String result) {
				if (!isFinishing()) {
					// Log.d(TAG, result);

					DecodeJson(result);
					getListView().invalidate();
					itsProgressLoading.dismiss();
				}
				super.onPostExecute(result);
			}
		}.execute();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent anIntent = new Intent(this, PlayerService.class);
		bindService(anIntent, svcConn, BIND_AUTO_CREATE);
		startService(anIntent);

		// gui stuff
		itsArrayAdapter = new RadioItemBigAdapter(this, R.layout.list_item_big);
		setListAdapter(itsArrayAdapter);

		RefillList(itsAdressWWWTopClick25);

		ListView lv = getListView();
		lv.setTextFilterEnabled(true);
		// registerForContextMenu(lv);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Object anObject = parent.getItemAtPosition(position);
				if (anObject instanceof RadioStation) {
					ClickOnItem((RadioStation) anObject);
				}
			}
		});
	}

	void ClickOnItem(RadioStation theStation) {
		// When clicked, show a toast with the TextView text
		RadioStation aStation = theStation;
		// itsProgressLoading = ProgressDialog.show(MainActivity.this,
		// "","Loading...");
		if (itsPlayerService != null) {
			try {
				itsPlayerService.Play(aStation.StreamUrl, aStation.Name, aStation.ID);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, "" + e);
			}
		} else {
			Log.v(TAG, "SERVICE NOT ONLINE");
		}
	}

	protected void DecodeJson(String result) {
		try {
			JSONArray jsonArray = new JSONArray(result);
			Log.v(TAG, "Found entries:" + jsonArray.length());
			itsArrayAdapter.clear();

			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject anObject = jsonArray.getJSONObject(i);
				// Log.v(TAG, "found station:" + anObject.getString("name"));

				RadioStation aStation = new RadioStation();
				aStation.Name = anObject.getString("name");
				aStation.StreamUrl = anObject.getString("url");
				aStation.Votes = anObject.getInt("votes");
				aStation.HomePageUrl = anObject.getString("homepage");
				aStation.TagsAll = anObject.getString("tags");
				aStation.Country = anObject.getString("country");
				aStation.IconUrl = anObject.getString("favicon");

				itsArrayAdapter.add(aStation);
			}

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	final int MENU_STOP = 0;
	final int MENU_TOPVOTE = 1;
	final int MENU_TOPCLICK = 2;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_STOP, Menu.NONE, "Stop");
		menu.add(Menu.NONE, MENU_TOPVOTE, Menu.NONE, "TopVote");
		menu.add(Menu.NONE, MENU_TOPCLICK, Menu.NONE, "TopClick");

		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.v(TAG, "menu click");

		if (item.getItemId() == MENU_STOP) {
			Log.v(TAG, "menu : stop");
			try {
				itsPlayerService.Stop();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, "" + e);
			}
			return true;
		}
		// check selected menu item
		if (item.getItemId() == MENU_TOPVOTE) {
			Log.v(TAG, "menu : topvote");
			RefillList(itsAdressWWWTopVote25);
			setTitle("TopVote");
			return true;
		}
		if (item.getItemId() == MENU_TOPCLICK) {
			Log.v(TAG, "menu : topclick");
			RefillList(itsAdressWWWTopClick25);
			setTitle("TopClick");
			return true;
		}
		return false;
	}

	public String downloadFeed(String theURI) {
		StringBuilder builder = new StringBuilder();
		HttpClient client = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(theURI);
		try {
			HttpResponse response = client.execute(httpGet);
			StatusLine statusLine = response.getStatusLine();
			int statusCode = statusLine.getStatusCode();
			if (statusCode == 200) {
				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(content));
				String line;
				while ((line = reader.readLine()) != null) {
					builder.append(line);
				}
			} else {
				Log.e(TAG, "Failed to download file");
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, "" + e);
		} catch (IOException e) {
			Log.e(TAG, "" + e);
		}
		return builder.toString();
	}
}
