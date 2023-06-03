package fr.jboll.obdrecorder;


import fr.jboll.obdrecorder.decoder.MessageValue;
import fr.jboll.obdrecorder.reader.MyObdDataReader;

import java.io.IOException;

public class PrintRecordingDataInConsoleLauncher {

	public static void main(String[] args) throws IOException {
		// reader
		MyObdDataReader reader = new MyObdDataReader();
		reader.setDataFile("FullCharge.zip");
		reader.setDbcFile("Model3CAN_updated.dbc");
		// skip messages by id, ie : reader.setMessageIdFilter(id -> id == 850 || id == 1025);
		// add messages reducer, ie : reader.setMessageAggregator(306, Duration.ofSeconds(1), candidates -> candidates.get(0));

		// iterate over data
		for (MessageValue messageValue : reader.readMyObdFile()) {
			System.err.println(messageValue);
		}

		// display some reading stats
		System.err.println(reader.getReadsStats());
	}

}