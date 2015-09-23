package com.guthub.com.maxrosan.cellstatis;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.support.v7.app.ActionBarActivity;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.Context;
import android.graphics.Color;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.jcraft.jsch.*;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.*;
import com.jjoe64.graphview.LegendRenderer;

class DownloadTask extends AsyncTask<String, Integer, String> {

    private Context context;
    //private PowerManager.WakeLock mWakeLock;

    public DownloadTask(Context context) {
        this.context = context;
    }

    @Override
    protected String doInBackground(String... sUrl) {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(sUrl[0]);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage();
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            int fileLength = connection.getContentLength();

            // download the file
            input = connection.getInputStream();
            output = new FileOutputStream("/sdcard/downloadfile");

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                // allow canceling with back button
                if (isCancelled()) {
                    input.close();
                    return null;
                }
                total += count;
                // publishing the progress....
                if (fileLength > 0) // only if total length is known
                    publishProgress((int) (total * 100 / fileLength));
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
        return null;
    }
}

public class MainActivity extends ActionBarActivity {

	private ListView myListView;
	private ArrayAdapter<String> listAdapter;
	private TelephonyManager telephonyManager;
	HashMap<Integer, String> networkType = new HashMap<Integer, String>();
	private Timer timer = new Timer();
	private HandlerValues handlerValues = new HandlerValues();
	private JSch jsch;
	private Session session;
	private GraphView graphView;
	private LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>();
	
	private final int numberOfDbms = 3;
	private ArrayList<LineGraphSeries<DataPoint>> seriesArray = new ArrayList<LineGraphSeries<DataPoint>>();
	
	private long rxBytes = 0;
	private long txBytes = 0;
	                                                                              
	private int counter = 0;
	
	private DownloadTask downloadTask = null;

	private class HandlerValues extends Handler {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);

			MainActivity.this.addValues();
		}
	}

	private class UpdateValues extends TimerTask {

		@Override
		public void run() {
			handlerValues.sendEmptyMessage(0);
		}

	}

	private void addValues() {

		listAdapter.setNotifyOnChange(false);
		listAdapter.clear();
		

		listAdapter.add("SIM MCC+MNC: " + telephonyManager.getSimOperator());

		if (networkType.containsKey(telephonyManager.getNetworkType())) {
			listAdapter.add("Network type: "
					+ networkType.get(telephonyManager.getNetworkType()));
		} else {
			listAdapter.add("Network type: Unknown");
		}

		listAdapter.add("Operator: "
				+ telephonyManager.getNetworkOperatorName());

		switch (telephonyManager.getDataActivity()) {

		case TelephonyManager.DATA_ACTIVITY_DORMANT:
			listAdapter.add("Data activity: Dormant");
			break;

		case TelephonyManager.DATA_ACTIVITY_IN:
			listAdapter.add("Data activity: RX");
			break;

		case TelephonyManager.DATA_ACTIVITY_OUT:
			listAdapter.add("Data activity: TX");
			break;

		case TelephonyManager.DATA_ACTIVITY_INOUT:
			listAdapter.add("Data activity: TX and RX");
			break;

		case TelephonyManager.DATA_ACTIVITY_NONE:
			listAdapter.add("Data activity: NO TRAFFIC");
			break;

		}
		
		if (rxBytes == 0) {
			
			rxBytes = TrafficStats.getMobileRxBytes();
			txBytes = TrafficStats.getMobileTxBytes();
			
		} else {
			
			if (TrafficStats.getMobileRxBytes() == TrafficStats.UNSUPPORTED) {
				listAdapter.add("RX : unsupported");
				listAdapter.add("TX : unsupported");
			} else {
				listAdapter.add("RX : " + (TrafficStats.getMobileRxBytes() - rxBytes) / 2000. + " kB/s");
				listAdapter.add("TX : " + (TrafficStats.getMobileTxBytes() - txBytes) / 2000. + " kB/s");
				rxBytes = TrafficStats.getMobileRxBytes();
				txBytes = TrafficStats.getMobileTxBytes();
			}
			
		}		

		int i = 0;
		ArrayList<Integer> dbms = new ArrayList<Integer>();

		for (CellInfo cell : telephonyManager.getAllCellInfo()) {

			String name = "";
			String type = "";
			String signal = "";
			String registered = "";
			String level = "";
			String id = "";
			int dbmValue = 0;
			
			if (cell instanceof CellInfoLte) {
				CellInfoLte cellLTE = (CellInfoLte) cell;

				type = "LTE";
				name = cellLTE.getCellIdentity().getMcc() + " "
						+ cellLTE.getCellIdentity().getMnc();
				signal = cellLTE.getCellSignalStrength().getDbm() + " dbm";
				level = "level: " + cellLTE.getCellSignalStrength().getLevel();
				id = "id: " + cellLTE.getCellIdentity().getPci();
				dbmValue = cellLTE.getCellSignalStrength().getDbm();
				
			} else if (cell instanceof CellInfoWcdma) {
				CellInfoWcdma cellWCDMA = (CellInfoWcdma) cell;

				type = "WCDMA";
				name = cellWCDMA.getCellIdentity().getMcc() + " "
						+ cellWCDMA.getCellIdentity().getMnc();
				signal = cellWCDMA.getCellSignalStrength().getDbm() + " dbm";
				level = "level: "
						+ cellWCDMA.getCellSignalStrength().getLevel();
				id = "id: " + cellWCDMA.getCellIdentity().getCid();
				dbmValue = cellWCDMA.getCellSignalStrength().getDbm();
			} else if (cell instanceof CellInfoGsm) {
				CellInfoGsm cellGSM = (CellInfoGsm) cell;

				type = "GSM";
				name = cellGSM.getCellIdentity().getMcc() + " "
						+ cellGSM.getCellIdentity().getMnc();
				signal = cellGSM.getCellSignalStrength().getDbm() + " dbm";
				level = "level: " + cellGSM.getCellSignalStrength().getLevel();
				id = "id: " + cellGSM.getCellIdentity().getCid();
				dbmValue = cellGSM.getCellSignalStrength().getDbm();
			}

			registered = Boolean.toString(cell.isRegistered());

			if (name.compareTo(Integer.MAX_VALUE + " " + Integer.MAX_VALUE) == 0) {
				name = "Unknown";
				id = "id: unknown";
			}

			listAdapter.add("Cell [" + type + "] " + name + " \n [" + signal
					+ "] [" + level + "] [" + registered + "] \n " + id);
			
			if (cell.isRegistered()) {
				series.setTitle("Cell " + id);
				series.appendData(new DataPoint(counter, dbmValue), true, 90);
			} else {
				dbms.add(dbmValue);
			}

		}
		
		Collections.sort(dbms);
		Collections.reverse(dbms);
		
		for (int j = 0; j < Math.min(dbms.size(), numberOfDbms); j++) {
			seriesArray.get(j).appendData(new DataPoint(counter, dbms.get(j)), true, 90);
		}
		
		counter++;
		
		listAdapter.notifyDataSetChanged();

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		networkType.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");
		networkType.put(TelephonyManager.NETWORK_TYPE_GPRS, "GPRS");
		networkType.put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
		networkType.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPAP");
		networkType.put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");
		networkType.put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");

		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

		myListView = (ListView) findViewById(R.id.mainListView);

		listAdapter = new ArrayAdapter<String>(this, R.layout.simplerow);
		
		myListView.setAdapter(listAdapter);

		//addValues();

		series.setTitle("Cell");
		series.setColor(Color.RED);
		
		graphView = (GraphView) findViewById(R.id.graph);
		graphView.getViewport().setMinY(-130);
		graphView.getViewport().setMaxY(30);
		
		graphView.getLegendRenderer().setVisible(true);
		graphView.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);		
		
		graphView.addSeries(series);
		
		for (int i = 0; i < numberOfDbms; i++) {
			LineGraphSeries<DataPoint> instanceLineSeries = new LineGraphSeries<DataPoint>();
			int color = 0;
			
			if (i == 0) {
				color = Color.MAGENTA;
			} else if (i == 1) {
				color = Color.GREEN;
			} else if (i == 2) {
				color = Color.BLUE;
			} else if (i == 3) {
				color = Color.DKGRAY;
			}
			
			instanceLineSeries.setColor(color);
			instanceLineSeries.setTitle("Best cell " + (i+1));
			
			seriesArray.add(instanceLineSeries);
			graphView.addSeries(instanceLineSeries);
		}
		
		timer.schedule(new UpdateValues(), 0, 2000);
		

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void createDownloadTask() {
		downloadTask = new DownloadTask(this);
		downloadTask.execute("http://releases.ubuntu.com/12.04/ubuntu-12.04.5-desktop-amd64.iso");
	}
	
	private void cancelDownloadTask() {
		if (downloadTask != null) {
			downloadTask.cancel(true);
			downloadTask = null;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		if (id == R.id.action_settings) {
			return true;
		} else if (id == R.id.action_dl) {
			createDownloadTask();
			return true;
		} else if (id == R.id.action_cancel_dl) {
			cancelDownloadTask();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
