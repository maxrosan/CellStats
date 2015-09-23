package com.guthub.com.maxrosan.cellstatis;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import android.content.Context;
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
	private int counter = 0;

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
				id = "id: " + cellLTE.getCellIdentity().getTac();
				dbmValue = cellLTE.getCellSignalStrength().getDbm();
				
			} else if (cell instanceof CellInfoWcdma) {
				CellInfoWcdma cellWCDMA = (CellInfoWcdma) cell;

				type = "WCDMA";
				name = cellWCDMA.getCellIdentity().getMcc() + " "
						+ cellWCDMA.getCellIdentity().getMnc();
				signal = cellWCDMA.getCellSignalStrength().getDbm() + " dbm";
				level = "level: "
						+ cellWCDMA.getCellSignalStrength().getLevel();
				id = "id: " + cellWCDMA.getCellIdentity().getLac();
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
				series.appendData(new DataPoint(counter++, dbmValue), true, 90);
			}

		}
		
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

		addValues();

		timer.schedule(new UpdateValues(), 1500, 1500);

		series.setTitle("Cell");
		
		graphView = (GraphView) findViewById(R.id.graph);
		graphView.getViewport().setMinY(-130);
		graphView.getViewport().setMaxY(30);
		
		graphView.getLegendRenderer().setVisible(true);
		graphView.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);		
		
		graphView.addSeries(series);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
