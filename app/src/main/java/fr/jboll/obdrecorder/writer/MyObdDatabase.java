package fr.jboll.obdrecorder.writer;

import fr.jboll.obdrecorder.decoder.MessageValue;
import fr.jboll.obdrecorder.decoder.SignalDoubleValue;
import fr.jboll.obdrecorder.decoder.SignalStringValue;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MyObdDatabase {

	private final InfluxDB influxDB;
	private final String databaseName = "myobd";

	public MyObdDatabase(String influxDbUrl) {
		this.influxDB = InfluxDBFactory.connect(influxDbUrl);
		this.influxDB.setDatabase(databaseName);
		this.influxDB.setRetentionPolicy("defaultPolicy");
	}

	public void deleteAllMessages() {
		deleteAllData("messages");
	}

	public void deleteAllData(String tableName) {
		this.influxDB.query(new Query("DELETE FROM " + tableName, databaseName));
	}

	public void deleteAllData() {
		this.influxDB.deleteDatabase(databaseName);
		this.influxDB.createDatabase(databaseName);
		this.influxDB.createRetentionPolicy("defaultPolicy", databaseName, "300000000d", 1, true);
	}


	public void writeMessage(MessageValue messageValue) {
		Map<String, Object> fields = new HashMap<>();
		fields.put("messageId", messageValue.type().messageId());
		write("messages", messageValue.currentDateTime(), fields);
	}

	public void writeSignals(MessageValue messageValue) {
		Map<String, Object> fields = new HashMap<>();

		for (SignalDoubleValue value : messageValue.doubleValues()) {
			fields.put(value.type().signalId(), value.value());
		}

		for (SignalStringValue value : messageValue.stringValues()) {
			fields.put(value.type().signalId(), value.value());
		}

		write(messageValue.type().messageName(), messageValue.currentDateTime(), fields);
	}

	public void write(String tableName, long currentTime, String key, Object value) {
		Map<String, Object> fields = new HashMap<>();
		fields.put(key, value);
		write(tableName, currentTime, fields);
	}

	public void write(String tableName, long currentTime, Map<String, Object> fields) {
		final Point point = Point.measurement(tableName)
				.time(currentTime, TimeUnit.MILLISECONDS)
				.fields(fields)
				.build();

		influxDB.write(point);
	}


}
