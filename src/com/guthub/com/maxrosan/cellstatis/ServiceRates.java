package com.guthub.com.maxrosan.cellstatis;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.widget.RemoteViews;
import android.widget.TextView;


public class ServiceRates extends Service {
	
	private class HandlerValues extends Handler {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);

			updateNotification();
		}
	}

	private class UpdateValues extends TimerTask {

		@Override
		public void run() {
			handlerValues.sendEmptyMessage(0);
		}

	}
	
	private final IBinder mBinder = (IBinder) new LocalBinder();
	private String txCell, rxCell;
	private long rxBytes, txBytes;
	private HandlerValues handlerValues = new HandlerValues();
	private Timer timer = new Timer();
	private String cellId = "Cell id:";

    public class LocalBinder extends Binder {
        ServiceRates getService() {
            return ServiceRates.this;
        }
    }	
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		
		rxBytes = TrafficStats.getMobileRxBytes();
		txBytes = TrafficStats.getMobileTxBytes();			
		
		timer.schedule(new UpdateValues(), 2000, 2000);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return START_NOT_STICKY;
	}
	
	public void cancelNotification() {
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	private void updateNotification() {
		
		if (TrafficStats.getMobileRxBytes() == TrafficStats.UNSUPPORTED) {
			
			rxCell = "unsupported";
			txCell = "unsupported";
	
		} else {
			
			String unit = "k";
			DecimalFormat myFormatter = new DecimalFormat("###.#");
			
			double rateRx = (TrafficStats.getMobileRxBytes() - rxBytes) / 2000.;
			double rateTx = (TrafficStats.getMobileTxBytes() - txBytes) / 2000.;			
			
			if (rateRx > 1000.) {
				unit = "M";
				rateRx /= 1000.;
				rateTx /= 1000.;
			} else if (rateRx > 99.9) {
				rateRx = Math.round(rateRx);
				rateTx = Math.round(rateTx);
			}
			
			rxCell = myFormatter.format(rateRx) + " " + unit + "B/s";
			txCell = myFormatter.format(rateTx) + " " + unit + "B/s";
			
			rxBytes = TrafficStats.getMobileRxBytes();
			txBytes = TrafficStats.getMobileTxBytes();
			
		}
		
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		
		Notification notification = new Notification(android.R.drawable.ic_dialog_info, "CellStats", System.currentTimeMillis());
		NotificationManager notificationMan = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
		
		RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_layout);
		contentView.setTextViewText(R.id.tdTextTX, "TX: " + txCell);
		contentView.setTextViewText(R.id.tdTextRX, "RX: " + rxCell);
		
		String type = "", name = "", signal = "", id = "";
		int dbmValue = 0, level = 0;
		
		for (CellInfo cell : telephonyManager.getAllCellInfo()) {
			if (cell.isRegistered()) {
				
				if (cell instanceof CellInfoLte) {
					CellInfoLte cellLTE = (CellInfoLte) cell;

					type = "LTE";
					name = cellLTE.getCellIdentity().getMcc() + " "
							+ cellLTE.getCellIdentity().getMnc();
					signal = cellLTE.getCellSignalStrength().getDbm() + " dbm";
					level = cellLTE.getCellSignalStrength().getLevel();
					id = "Cell id: " + cellLTE.getCellIdentity().getPci();
					dbmValue = cellLTE.getCellSignalStrength().getDbm();
					
				} else if (cell instanceof CellInfoWcdma) {
					CellInfoWcdma cellWCDMA = (CellInfoWcdma) cell;

					type = "WCDMA";
					name = cellWCDMA.getCellIdentity().getMcc() + " "
							+ cellWCDMA.getCellIdentity().getMnc();
					signal = cellWCDMA.getCellSignalStrength().getDbm() + " dbm";
					level = cellWCDMA.getCellSignalStrength().getLevel();
					id = "Cell id: " + cellWCDMA.getCellIdentity().getCid();
					dbmValue = cellWCDMA.getCellSignalStrength().getDbm();
				} else if (cell instanceof CellInfoGsm) {
					CellInfoGsm cellGSM = (CellInfoGsm) cell;

					type = "GSM";
					name = cellGSM.getCellIdentity().getMcc() + " "
							+ cellGSM.getCellIdentity().getMnc();
					signal = cellGSM.getCellSignalStrength().getDbm() + " dbm";
					level = cellGSM.getCellSignalStrength().getLevel();
					id = "Cell id: " + cellGSM.getCellIdentity().getCid();
					dbmValue = cellGSM.getCellSignalStrength().getDbm();
				}
				
				contentView.setTextViewText(R.id.tdCellId, id);
				contentView.setTextViewText(R.id.tdRAT, type);
				contentView.setTextViewText(R.id.tdSignalStrengh, dbmValue + " dbm");
				contentView.setTextViewText(R.id.tdLevel, MainActivity.calculateLevelString(level));
				
				if (level == 2) {
					contentView.setTextColor(R.id.tdLevel, Color.YELLOW);
				} else if (level == 1) {
					contentView.setTextColor(R.id.tdLevel, Color.RED);
				}
				
			}
		}		
		
		notification.contentView = contentView;
		
		Intent notificationIntent = new Intent();
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);
        notification.contentIntent = contentIntent;
        
        notification.flags |= Notification.FLAG_NO_CLEAR; //Do not clear the notification
        
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        //notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
        
        
		
		/*NotificationCompat.Builder builder =
				new NotificationCompat.Builder(this)
		.setContentTitle("CellStats")
	    .setContentText("TX: " + txCell + "\nRX: " + rxCell)
        .setAutoCancel(true)
        .setWhen(System.currentTimeMillis())
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setVisibility(Notification.VISIBILITY_PUBLIC);	
		
		Intent resultIntent = new Intent(this, MainActivity.class);
		
		PendingIntent resultPendingIntent = 
				PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		builder.setContentIntent(resultPendingIntent);*/
		
		//((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, builder.build());
        
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification);
		
	}

	public String getCellId() {
		return cellId;
	}

	public void setCellId(String cellId) {
		this.cellId = cellId;
	}
		


}
